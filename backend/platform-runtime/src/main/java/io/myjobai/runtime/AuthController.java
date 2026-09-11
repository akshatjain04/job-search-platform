package io.myjobai.runtime;

import io.myjobai.domain.*;
import io.myjobai.persistence.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "APP_ROLE", havingValue = "api", matchIfMissing = true)
public class AuthController {
  private final IdentityService identity;
  private final PlatformSettings settings;
  private final JsonRows rows;

  public AuthController(IdentityService identity, PlatformSettings settings, JsonRows rows) {
    this.identity = identity;
    this.settings = settings;
    this.rows = rows;
  }

  @GetMapping("/config")
  Map<String, Object> config() {
    return Map.of("testMode", settings.test(), "loginUrl", "/api/v1/auth/login");
  }

  @GetMapping("/login")
  ResponseEntity<Void> login() {
    var login = identity.beginLogin();
    return ResponseEntity.status(302)
        .header(HttpHeaders.SET_COOKIE, cookie("myjobai-login", login.state(), 600, "/api/v1/auth"))
        .location(URI.create(login.url()))
        .build();
  }

  @GetMapping("/callback")
  ResponseEntity<Void> callback(@RequestParam String code, HttpServletRequest request) {
    String state = SecurityConfiguration.cookie(request, "myjobai-login");
    if (state == null) throw DomainException.invalid("Missing login cookie; restart login");
    var session = identity.finishLogin(state, code);
    return ResponseEntity.status(302)
        .header(
            HttpHeaders.SET_COOKIE,
            cookie("myjobai-session", session.token(), 604800, "/"),
            cookie("myjobai-login", "", 0, "/api/v1/auth"))
        .location(URI.create(settings.publicUrl() + "/"))
        .build();
  }

  @PostMapping("/test-login")
  ResponseEntity<IdentityService.Principal> test(@RequestBody Map<String, String> body) {
    var session = identity.testLogin(body.getOrDefault("email", "demo@example.com"));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie("myjobai-session", session.token(), 86400, "/"))
        .body(session.principal());
  }

  @GetMapping("/session")
  IdentityService.Principal session(@AuthenticationPrincipal IdentityService.Principal principal) {
    return principal;
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @AuthenticationPrincipal IdentityService.Principal p, HttpServletRequest request) {
    identity.logout(SecurityConfiguration.cookie(request, "myjobai-session"), p.id());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("myjobai-session", "", 0, "/"))
        .build();
  }

  @PostMapping("/access-token")
  Map<String, Object> access(@AuthenticationPrincipal IdentityService.Principal p) {
    return Map.of("accessToken", identity.issueAccess(p.id(), "mcp"), "expiresIn", 3600);
  }

  @GetMapping("/extension/authorize")
  ResponseEntity<Void> extension(
      @AuthenticationPrincipal IdentityService.Principal p,
      @RequestParam String redirectUri,
      @RequestParam String challenge,
      @RequestParam String state) {
    return ResponseEntity.status(302)
        .location(URI.create(identity.extensionCode(p.id(), redirectUri, challenge, state)))
        .build();
  }

  public record Exchange(String code, String verifier, String redirectUri) {}

  @PostMapping("/extension/token")
  Map<String, Object> exchange(@RequestBody Exchange input) {
    return Map.of(
        "accessToken",
        identity.exchangeExtension(input.code(), input.verifier(), input.redirectUri()),
        "expiresIn",
        3600);
  }

  private String cookie(String name, String value, long seconds, String path) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(settings.secureCookies())
        .sameSite("Lax")
        .path(path)
        .maxAge(seconds)
        .build()
        .toString();
  }
}
