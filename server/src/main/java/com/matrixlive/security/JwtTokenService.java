package com.matrixlive.security;

import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
  private static final String CLAIM_KIND = "kind";
  private static final String CLAIM_USER_ID = "uid";
  private static final String CLAIM_PARTICIPANT_ID = "pid";
  private static final String CLAIM_DEVICE_ID = "did";
  private static final String CLAIM_ACTIVITY_ID = "aid";
  private static final String CLAIM_ROLE = "role";
  private final JwtProperties properties;
  private final SecretKey signingKey;

  public JwtTokenService(JwtProperties properties) {
    this.properties = properties;
    byte[] key = Decoders.BASE64.decode(properties.getSecret());
    if (key.length < 32) throw new IllegalStateException("JWT secret must contain at least 256 bits");
    this.signingKey = Keys.hmacShaKeyFor(key);
  }

  public IssuedAccessToken issueAccountToken(UserAccount account) {
    Instant expiresAt = Instant.now().plus(properties.getAccessTokenTtl());
    UUID tokenId = UUID.randomUUID();
    String token = issue(account.getUsername(), tokenId, expiresAt, Map.of(
        CLAIM_KIND, PrincipalKind.ACCOUNT.name(),
        CLAIM_USER_ID, account.getId().toString(),
        // A non-system account receives no global privilege. STAFF is only a baseline
        // authentication authority; activity capabilities are looked up from membership.
        CLAIM_ROLE, account.getSystemRole() == null ? UserRole.STAFF.name() : account.getSystemRole().name()));
    return new IssuedAccessToken(token, tokenId, expiresAt);
  }

  public IssuedAccessToken issueParticipantToken(UUID activityId, UUID participantId) {
    Instant expiresAt = Instant.now().plus(properties.getParticipantTokenTtl());
    UUID tokenId = UUID.randomUUID();
    String token = issue("participant:" + participantId, tokenId, expiresAt, Map.of(
        CLAIM_KIND, PrincipalKind.PARTICIPANT.name(),
        CLAIM_PARTICIPANT_ID, participantId.toString(),
        CLAIM_ACTIVITY_ID, activityId.toString(),
        CLAIM_ROLE, UserRole.PARTICIPANT.name()));
    return new IssuedAccessToken(token, tokenId, expiresAt);
  }

  public IssuedAccessToken issueScreenDeviceToken(UUID activityId, UUID deviceId) {
    Instant expiresAt = Instant.now().plus(properties.getParticipantTokenTtl());
    UUID tokenId = UUID.randomUUID();
    String token = issue("screen-device:" + deviceId, tokenId, expiresAt, Map.of(
        CLAIM_KIND, PrincipalKind.SCREEN_DEVICE.name(),
        CLAIM_DEVICE_ID, deviceId.toString(),
        CLAIM_ACTIVITY_ID, activityId.toString(),
        CLAIM_ROLE, UserRole.STAFF.name()));
    return new IssuedAccessToken(token, tokenId, expiresAt);
  }

  public TokenClaims parse(String token) {
    Claims claims = Jwts.parser().verifyWith(signingKey).requireIssuer(properties.getIssuer()).build()
        .parseSignedClaims(token).getPayload();
    PrincipalKind kind = PrincipalKind.valueOf(claims.get(CLAIM_KIND, String.class));
    UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    return new TokenClaims(
        UUID.fromString(claims.getId()),
        kind,
        parseUuid(claims.get(CLAIM_USER_ID, String.class)),
        parseUuid(claims.get(CLAIM_PARTICIPANT_ID, String.class)),
        parseUuid(claims.get(CLAIM_DEVICE_ID, String.class)),
        parseUuid(claims.get(CLAIM_ACTIVITY_ID, String.class)),
        role,
        claims.getSubject(),
        claims.getExpiration().toInstant());
  }

  private String issue(String subject, UUID tokenId, Instant expiresAt, Map<String, Object> claims) {
    return Jwts.builder().issuer(properties.getIssuer()).subject(subject).id(tokenId.toString())
        .issuedAt(Date.from(Instant.now())).expiration(Date.from(expiresAt)).claims(claims)
        .signWith(signingKey).compact();
  }

  private UUID parseUuid(String value) { return value == null || value.isBlank() ? null : UUID.fromString(value); }

  public record IssuedAccessToken(String value, UUID tokenId, Instant expiresAt) { }
  public record TokenClaims(UUID tokenId, PrincipalKind kind, UUID userId, UUID participantId, UUID deviceId, UUID activityId,
                            UserRole role, String username, Instant expiresAt) { }
}
