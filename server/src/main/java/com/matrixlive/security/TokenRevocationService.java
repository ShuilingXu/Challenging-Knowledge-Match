package com.matrixlive.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.matrixlive.security.auth.RefreshToken;
import com.matrixlive.security.auth.RefreshTokenRepository;
import com.matrixlive.security.auth.RevokedAccessToken;
import com.matrixlive.security.auth.RevokedAccessTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService {
  private final RevokedAccessTokenRepository revokedAccessTokens;
  private final RefreshTokenRepository refreshTokens;
  private final JwtProperties properties;
  /**
   * Remembers tokens proven not revoked so a participant polling the API does not ask the database
   * the same question on every request. A revoked token therefore stays usable for at most the
   * configured window; keep it short.
   */
  private final Cache<String, Boolean> knownActive;
  private final boolean cachingEnabled;

  public TokenRevocationService(RevokedAccessTokenRepository revokedAccessTokens, RefreshTokenRepository refreshTokens,
      JwtProperties properties, @Value("${app.cache.revocation-ttl-seconds:3}") long ttlSeconds,
      @Value("${app.cache.revocation-max-entries:20000}") long maxEntries) {
    this.revokedAccessTokens = revokedAccessTokens;
    this.refreshTokens = refreshTokens;
    this.properties = properties;
    this.cachingEnabled = ttlSeconds > 0;
    this.knownActive = Caffeine.newBuilder()
        .maximumSize(Math.max(1, maxEntries))
        .expireAfterWrite(Duration.ofSeconds(Math.max(1, ttlSeconds)))
        .build();
  }

  public boolean isAccessTokenRevoked(JwtTokenService.TokenClaims claims) {
    return isAccessTokenRevoked(claims.tokenId()) || claims.familyId() != null && isAccessTokenRevoked(claims.familyId());
  }

  @Transactional
  public void revokeFamily(UUID familyId, UUID userId, Instant until) {
    revokedAccessTokens.save(new RevokedAccessToken(familyId.toString(), until, userId));
    forgetTombstones(familyId);
  }

  public boolean isAccessTokenRevoked(UUID tokenId) {
    return !isKnownActive(tokenId.toString());
  }

  private boolean isKnownActive(String tokenId) {
    if (!cachingEnabled) return !isTombstoned(tokenId);
    return knownActive.get(tokenId, this::isTombstoneFree);
  }

  private boolean isTombstoneFree(String tokenId) {
    return !isTombstoned(tokenId);
  }

  private boolean isTombstoned(String tokenId) {
    return revokedAccessTokens.existsByTokenIdAndExpiresAtAfter(tokenId, Instant.now());
  }

  @Transactional
  public void revokeAccessToken(AuthenticatedPrincipal principal) {
    if (principal.familyId() != null) {
      revokeFamily(principal.familyId(), principal.userId(),
          Instant.now().plus(properties.getRefreshTokenTtl()).plus(properties.getParticipantTokenTtl()));
    }
    if (principal.expiresAt().isAfter(Instant.now())) {
      revokedAccessTokens.save(new RevokedAccessToken(principal.tokenId().toString(), principal.expiresAt(), principal.userId()));
      forgetTombstones(principal.tokenId());
    }
  }

  @Transactional
  public void revokeRefreshToken(RefreshToken token) { token.revoke(null); }

  @Transactional
  public void revokeRefreshFamily(UUID familyId) {
    var members = refreshTokens.findByFamilyIdAndRevokedAtIsNull(familyId);
    for (RefreshToken token : members) token.revoke(null);
    members.stream().map(RefreshToken::getExpiresAt).max(Instant::compareTo).ifPresent(until -> revokeFamily(familyId, members.getFirst().getUserId(), until));
  }

  private void forgetTombstones(UUID tokenId) {
    if (tokenId != null) knownActive.invalidate(tokenId.toString());
  }
}
