package com.matrixlive.security.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface HumanChallengeRepository extends JpaRepository<HumanChallenge, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from HumanChallenge c where c.id=:id")
  Optional<HumanChallenge> findForUpdate(@Param("id") UUID id);

  long countByIpAndCreatedAtAfter(String ip, Instant since);

  long countByContactHashAndCreatedAtAfter(String contactHash, Instant since);

  void deleteByCreatedAtBefore(Instant instant);
}
