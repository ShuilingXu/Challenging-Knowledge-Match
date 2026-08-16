package com.matrixlive.repository;

import com.matrixlive.domain.LotteryChance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotteryChanceRepository extends JpaRepository<LotteryChance, UUID> {
  Optional<LotteryChance> findByActivityIdAndParticipantId(UUID activityId, UUID participantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select chance from LotteryChance chance where chance.activityId = :activityId and chance.participantId = :participantId")
  Optional<LotteryChance> findByActivityIdAndParticipantIdForUpdate(@Param("activityId") UUID activityId,
      @Param("participantId") UUID participantId);
}
