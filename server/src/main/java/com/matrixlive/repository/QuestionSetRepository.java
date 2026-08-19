package com.matrixlive.repository;

import com.matrixlive.domain.QuestionSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, UUID> {
  List<QuestionSet> findByActivityIdOrderByUpdatedAtDesc(UUID activityId);
  Optional<QuestionSet> findByIdAndActivityId(UUID id, UUID activityId);
  Optional<QuestionSet> findByActivityIdAndActiveTrue(UUID activityId);
  boolean existsByActivityIdAndName(UUID activityId, String name);
}
