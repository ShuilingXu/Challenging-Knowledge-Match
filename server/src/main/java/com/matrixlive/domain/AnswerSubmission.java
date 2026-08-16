package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answer_submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "idempotencyKey"}))
public class AnswerSubmission {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID participantId;

  @Column(nullable = false)
  private UUID questionId;

  @Column(nullable = false, length = 160)
  private String idempotencyKey;

  @Column(columnDefinition = "text")
  private String submittedAnswers;

  private int awardedPoints;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(length = 1000)
  private String feedback;

  private Instant submittedAt;
  private Instant gradedAt;

  @Version
  private long version;

  protected AnswerSubmission() { }

  public AnswerSubmission(UUID activityId, UUID participantId, UUID questionId, String idempotencyKey,
      String submittedAnswers, int awardedPoints) {
    this(activityId, participantId, questionId, idempotencyKey, submittedAnswers, awardedPoints, "SCORED", null);
  }

  public AnswerSubmission(UUID activityId, UUID participantId, UUID questionId, String idempotencyKey,
      String submittedAnswers, int awardedPoints, String status, String feedback) {
    this.activityId = activityId;
    this.participantId = participantId;
    this.questionId = questionId;
    this.idempotencyKey = idempotencyKey;
    this.submittedAnswers = submittedAnswers == null ? "" : submittedAnswers;
    this.awardedPoints = awardedPoints;
    this.status = status;
    this.feedback = feedback;
    this.submittedAt = Instant.now();
    this.gradedAt = "SCORED".equals(status) ? submittedAt : null;
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getParticipantId() { return participantId; }
  public UUID getQuestionId() { return questionId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getSubmittedAnswers() { return submittedAnswers; }
  public int getAwardedPoints() { return awardedPoints; }
  public String getStatus() { return status; }
  public String getFeedback() { return feedback; }
  public Instant getSubmittedAt() { return submittedAt; }
  public Instant getGradedAt() { return gradedAt; }
  public long getVersion() { return version; }

  public void grade(int awardedPoints, String feedback) {
    this.awardedPoints = awardedPoints;
    this.feedback = feedback;
    this.status = "SCORED";
    this.gradedAt = Instant.now();
  }
}
