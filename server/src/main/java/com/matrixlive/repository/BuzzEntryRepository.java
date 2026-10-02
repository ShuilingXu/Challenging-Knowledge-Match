package com.matrixlive.repository;

import com.matrixlive.domain.BuzzEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuzzEntryRepository extends JpaRepository<BuzzEntry, UUID> {
  List<BuzzEntry> findByActivityIdAndQuestionIdOrderByResponseRankAsc(UUID activityId, UUID questionId);
  Optional<BuzzEntry> findByActivityIdAndQuestionIdAndParticipantId(UUID activityId, UUID questionId, UUID participantId);
  long countByActivityIdAndQuestionId(UUID activityId, UUID questionId);
  long countByActivityId(UUID activityId);
}
