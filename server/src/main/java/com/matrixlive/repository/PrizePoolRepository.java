package com.matrixlive.repository;

import com.matrixlive.domain.PrizePool;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrizePoolRepository extends JpaRepository<PrizePool, UUID> {
  Optional<PrizePool> findByActivityIdAndCode(UUID activityId, String code);
  List<PrizePool> findByActivityIdOrderByCreatedAtAsc(UUID activityId);
  List<PrizePool> findByActivityIdAndPurposeAndEnabledTrue(UUID activityId, String purpose);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select pool from PrizePool pool where pool.id = :id")
  Optional<PrizePool> findByIdForUpdate(@Param("id") UUID id);
}
