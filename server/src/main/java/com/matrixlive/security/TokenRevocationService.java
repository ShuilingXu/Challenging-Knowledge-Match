package com.matrixlive.security;

import com.matrixlive.security.auth.RefreshToken;
import com.matrixlive.security.auth.RefreshTokenRepository;
import com.matrixlive.security.auth.RevokedAccessToken;
import com.matrixlive.security.auth.RevokedAccessTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService {
  private final RevokedAccessTokenRepository revokedAccessTokens;
  private final RefreshTokenRepository refreshTokens;
  private final JwtProperties properties;

  public TokenRevocationService(RevokedAccessTokenRepository revokedAccessTokens, RefreshTokenRepository refreshTokens, JwtProperties properties) {
    this.revokedAccessTokens = revokedAccessTokens;
    this.refreshTokens = refreshTokens;
    this.properties = properties;
  }

  public boolean isAccessTokenRevoked(JwtTokenService.TokenClaims claims) {
    return isAccessTokenRevoked(claims.tokenId()) || claims.familyId() != null && isAccessTokenRevoked(claims.familyId());
  }

  @Transactional
  public void revokeFamily(UUID familyId, UUID userId, Instant until) {
    revokedAccessTokens.save(new RevokedAccessToken(familyId.toString(), until, userId));
  }

  public boolean isAccessTokenRevoked(UUID tokenId) {
    return revokedAccessTokens.existsByTokenIdAndExpiresAtAfter(tokenId.toString(), Instant.now());
  }

  @Transactional
  public void revokeAccessToken(AuthenticatedPrincipal principal) {
    if (principal.familyId() != null) revokeFamily(principal.familyId(),principal.userId(),Instant.now().plus(properties.getRefreshTokenTtl()).plus(properties.getParticipantTokenTtl()));
    if (principal.expiresAt().isAfter(Instant.now())) {
      revokedAccessTokens.save(new RevokedAccessToken(principal.tokenId().toString(), principal.expiresAt(), principal.userId()));
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
}
