package com.matrixlive.repository;

import com.matrixlive.domain.Question;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Question q where q.id = :id and q.activityId = :activityId")
  Optional<Question> findForAnswer(UUID id, UUID activityId);
  List<Question> findByActivityId(UUID activityId);
  List<Question> findByActivityIdOrderByDisplayOrderAsc(UUID activityId);
  long countByActivityId(UUID activityId);
}
