package com.matrixlive.security.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  Optional<RefreshToken> findByTokenHash(String tokenHash);
  @Query("select token.userId from RefreshToken token where token.tokenHash = :tokenHash")
  Optional<UUID> findUserIdByTokenHash(String tokenHash);
  List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
  List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);
  void deleteByExpiresAtBefore(Instant instant);
}
