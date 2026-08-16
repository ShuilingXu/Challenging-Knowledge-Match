package com.matrixlive.security.auth;

import static com.matrixlive.security.auth.AuthModels.*;

import com.matrixlive.security.AuthenticatedPrincipal;
import com.matrixlive.security.JwtProperties;
import com.matrixlive.security.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {
  private static final String REFRESH_COOKIE = "matrixlive_refresh";
  private final AuthService auth;
  private final TokenRevocationService revocations;
  private final JwtProperties properties;

  public AuthController(AuthService auth, TokenRevocationService revocations, JwtProperties properties) {
    this.auth = auth;
    this.revocations = revocations;
    this.properties = properties;
  }

  @PostMapping("/login")
  public ResponseEntity<AccessTokenResponse> login(@jakarta.validation.Valid @RequestBody LoginRequest request,
      HttpServletRequest servletRequest) {
    AuthService.AuthenticatedSession session = auth.login(request, servletRequest);
    return withRefreshCookie(session);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AccessTokenResponse> refresh(
      @CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
      @RequestBody(required = false) RefreshRequest request,
      HttpServletRequest servletRequest) {
    String rawToken = cookie != null ? cookie : request == null ? null : request.refreshToken();
    return withRefreshCookie(auth.refresh(rawToken, servletRequest));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
      @RequestBody(required = false) LogoutRequest request,
      HttpServletRequest servletRequest) {
    String rawToken = cookie != null ? cookie : request == null ? null : request.refreshToken();
    UUID userId = principal == null ? null : principal.userId();
    auth.logout(rawToken, servletRequest, userId);
    if (principal != null) revocations.revokeAccessToken(principal);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
  }

  @PostMapping("/participant-token")
  public ParticipantTokenResponse participantToken(@jakarta.validation.Valid @RequestBody ParticipantTokenRequest request,
      HttpServletRequest servletRequest) {
    return auth.participantToken(request, servletRequest);
  }

  @GetMapping("/me")
  public CurrentPrincipalResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return new CurrentPrincipalResponse(principal.kind().name(), principal.userId(), principal.participantId(),
        principal.activityId(), principal.username(), principal.role().name(), principal.expiresAt());
  }

  private ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthService.AuthenticatedSession session) {
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString()).body(session.response());
  }

  private ResponseCookie refreshCookie(String token) {
    return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(properties.isRefreshCookieSecure())
        .sameSite("Strict").path("/api/auth").maxAge(properties.getRefreshTokenTtl()).build();
  }

  private ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(properties.isRefreshCookieSecure())
        .sameSite("Strict").path("/api/auth").maxAge(0).build();
  }
}
