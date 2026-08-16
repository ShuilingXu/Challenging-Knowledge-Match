package com.matrixlive.security.auth;

import com.matrixlive.repository.ParticipantRepository;
import com.matrixlive.security.JwtProperties;
import com.matrixlive.security.JwtTokenService;
import com.matrixlive.security.JwtTokenService.IssuedAccessToken;
import com.matrixlive.service.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserAccountRepository users;
  private final ParticipantRepository participants;
  private final RefreshTokenRepository refreshTokens;
  private final JwtTokenService jwt;
  private final JwtProperties properties;
  private final PasswordEncoder passwordEncoder;
  private final SecurityAuditService audit;
  private final RequestMetadata requestMetadata;

  public AuthService(UserAccountRepository users, ParticipantRepository participants, RefreshTokenRepository refreshTokens,
      JwtTokenService jwt, JwtProperties properties, PasswordEncoder passwordEncoder, SecurityAuditService audit,
      RequestMetadata requestMetadata) {
    this.users = users;
    this.participants = participants;
    this.refreshTokens = refreshTokens;
    this.jwt = jwt;
    this.properties = properties;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
    this.requestMetadata = requestMetadata;
  }

  @Transactional
  public AuthenticatedSession login(AuthModels.LoginRequest request, HttpServletRequest servletRequest) {
    String username = request.username().trim();
    UserAccount account = users.findByUsernameIgnoreCase(username).orElse(null);
    if (account == null || !account.isEnabled() || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
      audit.record("AUTH_LOGIN_FAILED", "ACCOUNT", account == null ? null : account.getId(), null, false,
          requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Invalid credentials");
      throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
    AuthenticatedSession session = issueSession(account, null, servletRequest);
    audit.record("AUTH_LOGIN_SUCCEEDED", "ACCOUNT", account.getId(), null, true,
        requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Password login");
    return session;
  }

  @Transactional
  public AuthenticatedSession refresh(String rawToken, HttpServletRequest servletRequest) {
    if (rawToken == null || rawToken.isBlank()) throw new DomainException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
    String tokenHash = hash(rawToken);
    RefreshToken existing = refreshTokens.findByTokenHash(tokenHash)
        .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    if (!existing.isActive(Instant.now())) {
      if (existing.getRevokedAt() != null) {
        for (RefreshToken token : refreshTokens.findByFamilyIdAndRevokedAtIsNull(existing.getFamilyId())) token.revoke(null);
        audit.record("AUTH_REFRESH_REUSE_DETECTED", "ACCOUNT", existing.getUserId(), null, false,
            requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Refresh family revoked");
      }
      throw new DomainException(HttpStatus.UNAUTHORIZED, "Refresh token has expired or was revoked");
    }
    UserAccount account = users.findById(existing.getUserId())
        .filter(UserAccount::isEnabled)
        .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));
    AuthenticatedSession session = issueSession(account, existing.getFamilyId(), servletRequest);
    existing.revoke(hash(session.refreshToken()));
    audit.record("AUTH_TOKEN_REFRESHED", "ACCOUNT", account.getId(), null, true,
        requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Refresh rotation");
    return session;
  }

  public AuthModels.ParticipantTokenResponse participantToken(AuthModels.ParticipantTokenRequest request,
      HttpServletRequest servletRequest) {
    String contact = normalizeContact(request.contact());
    var participant = participants.findByActivityIdAndVenueAndContact(request.activityId(), request.venue(), contact)
        .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Registration was not found"));
    IssuedAccessToken token = jwt.issueParticipantToken(request.activityId(), participant.getId());
    audit.record("PARTICIPANT_SESSION_ISSUED", "PARTICIPANT", participant.getId(), request.activityId(), true,
        requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Contact-bound participant session");
    return new AuthModels.ParticipantTokenResponse(token.value(), "Bearer", token.expiresAt(), participant.getId(), request.activityId());
  }

  @Transactional
  public void logout(String rawRefreshToken, HttpServletRequest servletRequest, UUID accountId) {
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      refreshTokens.findByTokenHash(hash(rawRefreshToken)).filter(token -> token.isActive(Instant.now()))
          .ifPresent(token -> token.revoke(null));
    }
    audit.record("AUTH_LOGOUT", "ACCOUNT", accountId, null, true,
        requestMetadata.ipAddress(servletRequest), requestMetadata.userAgent(servletRequest), "Token revoked");
  }

  private AuthenticatedSession issueSession(UserAccount account, UUID existingFamilyId, HttpServletRequest request) {
    IssuedAccessToken access = jwt.issueAccountToken(account);
    String rawRefresh = randomToken();
    refreshTokens.save(new RefreshToken(account.getId(), existingFamilyId == null ? UUID.randomUUID() : existingFamilyId,
        hash(rawRefresh), Instant.now().plus(properties.getRefreshTokenTtl()), requestMetadata.ipAddress(request),
        requestMetadata.userAgent(request)));
    return new AuthenticatedSession(toResponse(account, access), rawRefresh);
  }

  private AuthModels.AccessTokenResponse toResponse(UserAccount account, IssuedAccessToken token) {
    return new AuthModels.AccessTokenResponse(token.value(), "Bearer", token.expiresAt(), account.getId(),
        account.getUsername(), account.getDisplayName(), account.getSystemRole() == null ? null : account.getSystemRole().name());
  }

  private String randomToken() {
    byte[] bytes = new byte[48];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String normalizeContact(String contact) { return contact.replaceAll("[\\s-]", "").trim(); }

  public record AuthenticatedSession(AuthModels.AccessTokenResponse response, String refreshToken) { }
}
