package io.myjobai.runtime;

import static io.myjobai.persistence.JsonRows.*;

import io.myjobai.domain.*;
import io.myjobai.persistence.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;

public class IdentityService {
  public record Principal(UUID id, String email, String csrf, String method) {
    @Override
    public String toString() {
      return "Principal[id=" + id + ", method=" + method + "]";
    }
  }

  public record Login(String url, String state) {}

  public record Session(String token, Principal principal) {
    @Override
    public String toString() {
      return "Session[redacted]";
    }
  }

  private final JsonRows rows;
  private final TokenCipher cipher;
  private final PlatformSettings settings;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final JwtDecoder decoder;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public IdentityService(
      JsonRows rows, TokenCipher cipher, PlatformSettings settings, Clock clock) {
    this.rows = rows;
    this.cipher = cipher;
    this.settings = settings;
    this.clock = clock;
    if (settings.test()) decoder = null;
    else {
      var jwt =
          NimbusJwtDecoder.withJwkSetUri(
                  settings.required("SUPABASE_URL") + "/auth/v1/.well-known/jwks.json")
              .jwsAlgorithm(SignatureAlgorithm.ES256)
              .jwsAlgorithm(SignatureAlgorithm.RS256)
              .build();
      jwt.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer(settings.required("SUPABASE_URL") + "/auth/v1"),
              token ->
                  token.getAudience().contains("authenticated")
                      ? OAuth2TokenValidatorResult.success()
                      : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
      decoder = jwt;
    }
  }

  public String randomToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String challenge(String verifier) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void extensionRedirect(String redirect) {
    boolean allowed =
        Arrays.stream(settings.get("EXTENSION_IDS", "").split(","))
            .filter(id -> id.matches("[a-p]{32}"))
            .anyMatch(id -> redirect.equals("https://" + id + ".chromiumapp.org/"));
    if (!allowed) throw DomainException.invalid("Extension is not registered in EXTENSION_IDS");
  }

  @Transactional
  public String extensionCode(UUID user, String redirect, String challenge, String state) {
    extensionRedirect(redirect);
    if (!challenge.matches("[A-Za-z0-9_-]{43}") || !state.matches("[A-Za-z0-9_-]{32,128}"))
      throw DomainException.invalid("Invalid extension PKCE challenge or state");
    String code = randomToken();
    rows.jdbc.update(
        "INSERT INTO app.extension_codes(code_hash,user_id,challenge,redirect_uri,expires_at) VALUES (?,?,?,?,?)",
        Normalization.hash(code),
        user,
        challenge,
        redirect,
        timestamp(clock.instant().plusSeconds(120)));
    return redirect + "?code=" + encode(code) + "&state=" + encode(state);
  }

  @Transactional
  public String exchangeExtension(String code, String verifier, String redirect) {
    extensionRedirect(redirect);
    if (code == null || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))
      throw DomainException.invalid("Invalid extension authorization exchange");
    var found =
        rows.jdbc.queryForList(
            "DELETE FROM app.extension_codes WHERE code_hash=? AND challenge=? AND redirect_uri=? AND expires_at>? RETURNING user_id",
            Normalization.hash(code),
            challenge(verifier),
            redirect,
            timestamp(clock.instant()));
    if (found.size() != 1)
      throw DomainException.invalid("Authorization code expired, used, or invalid PKCE verifier");
    return issueAccess((UUID) found.getFirst().get("user_id"), "extension");
  }

  @Transactional
  public Login beginLogin() {
    String state = randomToken(), verifier = randomToken();
    rows.jdbc.update(
        "INSERT INTO app.oauth_states(state_hash,flow,encrypted_verifier,redirect_uri,expires_at) VALUES (?,'login',?,?,?)",
        Normalization.hash(state),
        cipher.encrypt(verifier, "login:" + state),
        settings.publicUrl() + "/api/v1/auth/callback",
        timestamp(clock.instant().plusSeconds(600)));
    String url =
        settings.required("SUPABASE_URL")
            + "/auth/v1/authorize?provider="
            + encode(settings.get("AUTH_SOCIAL_PROVIDER", "google"))
            + "&redirect_to="
            + encode(settings.publicUrl() + "/api/v1/auth/callback")
            + "&code_challenge="
            + challenge(verifier)
            + "&code_challenge_method=s256";
    return new Login(url, state);
  }

  @Transactional
  public Session finishLogin(String state, String code) {
    var pending =
        rows.jdbc.queryForList(
            "DELETE FROM app.oauth_states WHERE state_hash=? AND flow='login' AND expires_at>? RETURNING encrypted_verifier",
            Normalization.hash(state),
            timestamp(clock.instant()));
    if (pending.size() != 1) throw DomainException.invalid("Login session expired or invalid");
    String verifier =
        cipher.decrypt(
            String.valueOf(pending.getFirst().get("encrypted_verifier")), "login:" + state);
    var tokens = tokenRequest("pkce", Map.of("auth_code", code, "code_verifier", verifier));
    var jwt = decoder.decode(tokens.path("access_token").asText());
    UUID user = UUID.fromString(jwt.getSubject());
    String email = Checks.email(jwt.getClaimAsString("email"));
    ensureUser(user, email);
    return createSession(
        user,
        email,
        tokens.path("access_token").asText(),
        tokens.path("refresh_token").asText(),
        jwt.getExpiresAt());
  }

  private tools.jackson.databind.JsonNode tokenRequest(String grant, Map<String, String> payload) {
    try {
      var request =
          HttpRequest.newBuilder(
                  URI.create(
                      settings.required("SUPABASE_URL") + "/auth/v1/token?grant_type=" + grant))
              .timeout(Duration.ofSeconds(20))
              .header("apikey", settings.required("SUPABASE_ANON_KEY"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(rows.write(payload)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw DomainException.invalid("Identity provider rejected login or session refresh");
      return rows.json.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw DomainException.invalid("Login cancelled");
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      throw DomainException.invalid("Identity provider unavailable");
    }
  }

  @Transactional
  public Session testLogin(String email) {
    if (!settings.test()) throw DomainException.missing();
    email = Checks.email(email);
    UUID user = UUID.nameUUIDFromBytes(("test:" + email).getBytes(StandardCharsets.UTF_8));
    ensureUser(user, email);
    return createSession(user, email, "", "", clock.instant().plusSeconds(86400));
  }

  private void ensureUser(UUID id, String email) {
    rows.jdbc.update(
        "INSERT INTO app.users(id,email) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET email=excluded.email",
        id,
        email);
  }

  private Session createSession(
      UUID user, String email, String access, String refresh, Instant expiry) {
    String token = randomToken(), csrf = randomToken();
    rows.jdbc.update(
        "INSERT INTO app.auth_sessions(token_hash,user_id,csrf_token,encrypted_access_token,encrypted_refresh_token,token_expires_at,expires_at) VALUES (?,?,?,?,?,?,?)",
        Normalization.hash(token),
        user,
        csrf,
        cipher.encrypt(access, user + ":session-access"),
        cipher.encrypt(refresh, user + ":session-refresh"),
        timestamp(expiry),
        timestamp(clock.instant().plusSeconds(86400 * 7)));
    rows.jdbc.update(
        "INSERT INTO app.audit_events(id,user_id,action,resource_type,resource_id) VALUES (?,?,'LOGIN','user',?)",
        UUID.randomUUID(),
        user,
        user);
    return new Session(token, new Principal(user, email, csrf, "cookie"));
  }

  @Transactional
  public Optional<Principal> cookie(String raw) {
    if (raw == null || raw.length() > 200) return Optional.empty();
    var list =
        rows.jdbc.queryForList(
            "SELECT s.*,u.email FROM app.auth_sessions s JOIN app.users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? FOR UPDATE OF s",
            Normalization.hash(raw),
            timestamp(clock.instant()));
    if (list.isEmpty()) return Optional.empty();
    var row = list.getFirst();
    UUID user = (UUID) row.get("user_id");
    Instant expires = ((java.sql.Timestamp) row.get("token_expires_at")).toInstant();
    if (!settings.test() && expires.isBefore(clock.instant().plusSeconds(30))) {
      var tokens =
          tokenRequest(
              "refresh_token",
              Map.of(
                  "refresh_token",
                  cipher.decrypt(
                      (String) row.get("encrypted_refresh_token"), user + ":session-refresh")));
      var jwt = decoder.decode(tokens.path("access_token").asText());
      if (!jwt.getSubject().equals(user.toString()))
        throw DomainException.invalid("Refreshed session identity mismatch");
      rows.jdbc.update(
          "UPDATE app.auth_sessions SET encrypted_access_token=?,encrypted_refresh_token=?,token_expires_at=? WHERE token_hash=?",
          cipher.encrypt(tokens.path("access_token").asText(), user + ":session-access"),
          cipher.encrypt(tokens.path("refresh_token").asText(), user + ":session-refresh"),
          timestamp(jwt.getExpiresAt()),
          Normalization.hash(raw));
    }
    return Optional.of(
        new Principal(user, (String) row.get("email"), (String) row.get("csrf_token"), "cookie"));
  }

  public Optional<Principal> bearer(String raw) {
    if (raw == null || raw.length() > 16000) return Optional.empty();
    if (raw.startsWith("mja_"))
      return rows
          .jdbc
          .query(
              "SELECT c.user_id,u.email FROM app.access_credentials c JOIN app.users u ON u.id=c.user_id WHERE c.token_hash=? AND c.expires_at>?",
              (r, n) -> new Principal(uuid(r, "user_id"), r.getString("email"), "", "bearer"),
              Normalization.hash(raw),
              timestamp(clock.instant()))
          .stream()
          .findFirst();
    if (decoder == null) return Optional.empty();
    try {
      var jwt = decoder.decode(raw);
      UUID id = UUID.fromString(jwt.getSubject());
      String email = Checks.email(jwt.getClaimAsString("email"));
      ensureUser(id, email);
      return Optional.of(new Principal(id, email, "", "bearer"));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public String issueAccess(UUID user, String purpose) {
    String token = "mja_" + randomToken();
    rows.jdbc.update(
        "INSERT INTO app.access_credentials(token_hash,user_id,purpose,expires_at) VALUES (?,?,?,?)",
        Normalization.hash(token),
        user,
        purpose,
        timestamp(clock.instant().plusSeconds(3600)));
    return token;
  }

  public void logout(String raw, UUID user) {
    if (raw != null)
      rows.jdbc.update(
          "DELETE FROM app.auth_sessions WHERE token_hash=? AND user_id=?",
          Normalization.hash(raw),
          user);
    rows.jdbc.update("DELETE FROM app.access_credentials WHERE user_id=?", user);
  }

  public static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
