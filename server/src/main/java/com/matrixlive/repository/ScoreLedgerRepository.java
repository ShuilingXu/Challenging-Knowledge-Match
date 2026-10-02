package com.matrixlive.repository;

import com.matrixlive.domain.ScoreLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreLedgerRepository extends JpaRepository<ScoreLedger, UUID> {
  interface ParticipantScore {
    UUID getParticipantId();
    Long getTotal();
    java.time.Instant getLastScoreAt();
  }
  @org.springframework.data.jpa.repository.Query("select s.participantId as participantId, sum(s.points) as total, max(s.createdAt) as lastScoreAt from ScoreLedger s where s.activityId = :activityId group by s.participantId")
  List<ParticipantScore> activityScores(UUID activityId);
  java.util.Optional<ScoreLedger> findByActivityIdAndIdempotencyKey(UUID activityId, String idempotencyKey);
  List<ScoreLedger> findByActivityIdAndParticipantIdOrderByCreatedAtAsc(UUID activityId, UUID participantId);
  List<ScoreLedger> findByActivityIdOrderByCreatedAtDesc(UUID activityId);
}
