package com.matrixlive.security.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {
  boolean existsByTokenIdAndExpiresAtAfter(String tokenId, Instant instant);
  void deleteByExpiresAtBefore(Instant instant);
}
