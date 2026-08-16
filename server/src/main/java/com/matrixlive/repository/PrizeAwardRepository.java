package com.matrixlive.repository;

import com.matrixlive.domain.PrizeAward;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrizeAwardRepository extends JpaRepository<PrizeAward, UUID> {
  List<PrizeAward> findByActivityIdAndParticipantId(UUID activityId, UUID participantId);
  List<PrizeAward> findByActivityIdOrderByAwardedAtDesc(UUID activityId);
  List<PrizeAward> findByActivityIdAndStatusOrderByAwardedAtDesc(UUID activityId, String status);
  boolean existsByActivityIdAndPrizePoolIdAndParticipantId(UUID activityId, UUID prizePoolId, UUID participantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select award from PrizeAward award where award.id = :id")
  Optional<PrizeAward> findByIdForUpdate(@Param("id") UUID id);
}
