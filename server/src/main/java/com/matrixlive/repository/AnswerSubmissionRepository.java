package com.matrixlive.repository;

import com.matrixlive.domain.AnswerSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerSubmissionRepository extends JpaRepository<AnswerSubmission, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select s from AnswerSubmission s where s.id=:id")
  Optional<AnswerSubmission> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  Optional<AnswerSubmission> findByIdempotencyKey(String idempotencyKey);
  Optional<AnswerSubmission> findByActivityIdAndIdempotencyKey(UUID activityId, String idempotencyKey);
  Optional<AnswerSubmission> findByActivityIdAndParticipantIdAndQuestionId(UUID activityId, UUID participantId, UUID questionId);
  List<AnswerSubmission> findByActivityIdAndParticipantIdOrderBySubmittedAtDesc(UUID activityId, UUID participantId);
  List<AnswerSubmission> findByActivityIdAndQuestionIdOrderBySubmittedAtAsc(UUID activityId, UUID questionId);
  List<AnswerSubmission> findByActivityIdOrderBySubmittedAtDesc(UUID activityId);
  long countByActivityIdAndQuestionId(UUID activityId, UUID questionId);
  boolean existsByActivityIdAndQuestionId(UUID activityId, UUID questionId);
}
