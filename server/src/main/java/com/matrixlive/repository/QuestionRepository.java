package com.matrixlive.repository;

import com.matrixlive.domain.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
  List<Question> findByActivityId(UUID activityId);
  List<Question> findByActivityIdOrderByDisplayOrderAsc(UUID activityId);
  long countByActivityId(UUID activityId);
}
