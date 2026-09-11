package io.myjobai.runtime;

import static io.myjobai.persistence.JsonRows.timestamp;

import io.myjobai.application.*;
import io.myjobai.domain.*;
import io.myjobai.persistence.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

/** Confidential-client OAuth. Access and refresh tokens never cross the browser boundary. */
public class MailboxOAuth {
  private final JsonRows rows;
  private final Ports.Mailboxes boxes;
  private final TokenCipher cipher;
  private final PlatformSettings settings;
  private final IdentityService identity;
  private final Clock clock;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public MailboxOAuth(
      JsonRows rows,
      Ports.Mailboxes boxes,
      TokenCipher cipher,
      PlatformSettings settings,
      IdentityService identity,
      Clock clock) {
    this.rows = rows;
    this.boxes = boxes;
    this.cipher = cipher;
    this.settings = settings;
    this.identity = identity;
    this.clock = clock;
  }

  private record Provider(
      String key,
      String authorize,
      String token,
      String client,
      String secret,
      String scopes,
      String userInfo) {}

  private Provider provider(String key) {
    if (key.equals("gmail"))
      return new Provider(
          key,
          "https://accounts.google.com/o/oauth2/v2/auth",
          "https://oauth2.googleapis.com/token",
          settings.required("GMAIL_CLIENT_ID"),
          settings.required("GMAIL_CLIENT_SECRET"),
          "openid email https://www.googleapis.com/auth/gmail.send",
          "https://openidconnect.googleapis.com/v1/userinfo");
    if (key.equals("outlook")) {
      String tenant = settings.get("MICROSOFT_TENANT_ID", "common");
      if (!tenant.matches("[A-Za-z0-9.-]{1,100}"))
        throw DomainException.invalid("Invalid MICROSOFT_TENANT_ID");
      String base = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/";
      return new Provider(
          key,
          base + "authorize",
          base + "token",
          settings.required("MICROSOFT_CLIENT_ID"),
          settings.required("MICROSOFT_CLIENT_SECRET"),
          "openid offline_access https://graph.microsoft.com/User.Read https://graph.microsoft.com/Mail.Send",
          "https://graph.microsoft.com/v1.0/me?$select=mail,userPrincipalName");
    }
    throw DomainException.invalid("Mailbox provider must be gmail or outlook");
  }

  private String callback(String provider) {
    return settings.publicUrl() + "/api/v1/mailboxes/" + provider + "/callback";
  }

  @Transactional
  public String begin(UUID user, String key) {
    Provider p = provider(key);
    String state = identity.randomToken(), verifier = identity.randomToken();
    rows.jdbc.update(
        "INSERT INTO app.oauth_states(state_hash,flow,user_id,encrypted_verifier,redirect_uri,expires_at) VALUES (?,?,?,?,?,?)",
        Normalization.hash(state),
        "mail:" + key,
        user,
        cipher.encrypt(verifier, user + ":mail-state:" + state),
        callback(key),
        timestamp(clock.instant().plusSeconds(600)));
    var params = new LinkedHashMap<String, String>();
    params.put("client_id", p.client());
    params.put("redirect_uri", callback(key));
    params.put("response_type", "code");
    params.put("scope", p.scopes());
    params.put("state", state);
    params.put("code_challenge", IdentityService.challenge(verifier));
    params.put("code_challenge_method", "S256");
    if (key.equals("gmail")) {
      params.put("access_type", "offline");
      params.put("prompt", "consent");
    } else params.put("response_mode", "query");
    return p.authorize() + "?" + form(params);
  }

  @Transactional
  public void finish(UUID user, String key, String state, String code) {
    Provider p = provider(key);
    Checks.text(state, "OAuth state", 200);
    Checks.text(code, "OAuth code", 8000);
    var pending =
        rows.jdbc.queryForList(
            "DELETE FROM app.oauth_states WHERE state_hash=? AND user_id=? AND flow=? AND expires_at>? RETURNING encrypted_verifier",
            Normalization.hash(state),
            user,
            "mail:" + key,
            timestamp(clock.instant()));
    if (pending.size() != 1)
      throw DomainException.invalid("Mailbox authorization expired or belongs to another session");
    String verifier =
        cipher.decrypt(
            (String) pending.getFirst().get("encrypted_verifier"), user + ":mail-state:" + state);
    var tokens =
        exchange(
            p,
            Map.of(
                "grant_type",
                "authorization_code",
                "code",
                code,
                "redirect_uri",
                callback(key),
                "code_verifier",
                verifier));
    String access = tokens.path("access_token").asText();
    var info =
        request(
            HttpRequest.newBuilder(URI.create(p.userInfo()))
                .header("Authorization", "Bearer " + access)
                .GET());
    String address =
        key.equals("gmail") ? info.path("email").asText() : info.path("mail").asText("");
    if (address.isBlank()) address = info.path("userPrincipalName").asText();
    if (key.equals("gmail") && !info.path("email_verified").asBoolean())
      throw DomainException.invalid("Mailbox address is not verified by Google");
    String refresh = tokens.path("refresh_token").asText();
    if (refresh.isBlank())
      throw DomainException.invalid(
          "No offline access granted; reconnect and consent to offline mailbox access");
    boxes.save(
        new Ports.Mailbox(user, key, Checks.email(address), access, refresh, expiry(tokens)));
    rows.jdbc.update(
        "INSERT INTO app.audit_events(id,user_id,action,resource_type,resource_id) VALUES (?,?,'MAILBOX_CONNECTED','user',?)",
        UUID.randomUUID(),
        user,
        user);
  }

  @Transactional
  public Ports.Mailbox ready(UUID user) {
    rows.jdbc.queryForList(
        "SELECT user_id FROM app.mailbox_connections WHERE user_id=? FOR UPDATE", user);
    var box =
        boxes
            .find(user)
            .orElseThrow(
                () ->
                    new IntegrationException(
                        "MAILBOX_MISSING", "Connect a Gmail or Outlook mailbox", false, false));
    if (box.expiresAt().isAfter(clock.instant().plusSeconds(60))) return box;
    var token =
        exchange(
            provider(box.provider()),
            Map.of("grant_type", "refresh_token", "refresh_token", box.refreshToken()));
    String refresh = token.path("refresh_token").asText(box.refreshToken());
    var updated =
        new Ports.Mailbox(
            user,
            box.provider(),
            box.address(),
            token.path("access_token").asText(),
            refresh,
            expiry(token));
    boxes.save(updated);
    return updated;
  }

  public Map<String, Object> status(UUID user) {
    return boxes
        .find(user)
        .<Map<String, Object>>map(
            b ->
                Map.of(
                    "connected",
                    true,
                    "provider",
                    b.provider(),
                    "address",
                    b.address(),
                    "expiresAt",
                    b.expiresAt()))
        .orElse(Map.of("connected", false));
  }

  @Transactional
  public void disconnect(UUID user) {
    rows.jdbc.update("DELETE FROM app.mailbox_connections WHERE user_id=?", user);
    rows.jdbc.update(
        "INSERT INTO app.audit_events(id,user_id,action,resource_type,resource_id) VALUES (?,?,'MAILBOX_DISCONNECTED','user',?)",
        UUID.randomUUID(),
        user,
        user);
  }

  private Instant expiry(tools.jackson.databind.JsonNode tokens) {
    if (tokens.path("access_token").asText().isBlank())
      throw new IntegrationException("MAIL_OAUTH", "Provider omitted access token", false, false);
    return clock
        .instant()
        .plusSeconds(Math.max(60, Math.min(86400, tokens.path("expires_in").asLong(3600))));
  }

  private tools.jackson.databind.JsonNode exchange(Provider p, Map<String, String> values) {
    var all = new LinkedHashMap<>(values);
    all.put("client_id", p.client());
    all.put("client_secret", p.secret());
    return request(
        HttpRequest.newBuilder(URI.create(p.token()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(all))));
  }

  private tools.jackson.databind.JsonNode request(HttpRequest.Builder request) {
    try {
      var response =
          http.send(
              request.timeout(Duration.ofSeconds(20)).build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() > 299)
        throw new IntegrationException(
            "MAIL_OAUTH",
            "Mailbox authorization failed; reconnect or check provider configuration",
            response.statusCode() == 429 || response.statusCode() >= 500,
            false);
      return rows.json.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(
          "MAIL_OAUTH_CANCELLED", "Mailbox authorization cancelled", true, false);
    } catch (IntegrationException e) {
      throw e;
    } catch (Exception e) {
      throw new IntegrationException(
          "MAIL_OAUTH_UNAVAILABLE", "Mailbox authorization provider unavailable", true, false);
    }
  }

  private static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> IdentityService.encode(e.getKey()) + "=" + IdentityService.encode(e.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
  }
}
