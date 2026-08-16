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

  public TokenRevocationService(RevokedAccessTokenRepository revokedAccessTokens, RefreshTokenRepository refreshTokens) {
    this.revokedAccessTokens = revokedAccessTokens;
    this.refreshTokens = refreshTokens;
  }

  public boolean isAccessTokenRevoked(UUID tokenId) {
    return revokedAccessTokens.existsByTokenIdAndExpiresAtAfter(tokenId.toString(), Instant.now());
  }

  @Transactional
  public void revokeAccessToken(AuthenticatedPrincipal principal) {
    if (principal.userId() != null && principal.expiresAt().isAfter(Instant.now())) {
      revokedAccessTokens.save(new RevokedAccessToken(principal.tokenId().toString(), principal.expiresAt(), principal.userId()));
    }
  }

  @Transactional
  public void revokeRefreshToken(RefreshToken token) { token.revoke(null); }

  @Transactional
  public void revokeRefreshFamily(UUID familyId) {
    for (RefreshToken token : refreshTokens.findByFamilyIdAndRevokedAtIsNull(familyId)) token.revoke(null);
  }
}
