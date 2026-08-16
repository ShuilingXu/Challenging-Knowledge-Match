package com.matrixlive.repository;

import com.matrixlive.domain.ScoreLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreLedgerRepository extends JpaRepository<ScoreLedger, UUID> {
  List<ScoreLedger> findByActivityIdAndParticipantIdOrderByCreatedAtAsc(UUID activityId, UUID participantId);
  List<ScoreLedger> findByActivityIdOrderByCreatedAtDesc(UUID activityId);
}
