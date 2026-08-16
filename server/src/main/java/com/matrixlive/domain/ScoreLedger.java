package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_ledgers")
public class ScoreLedger {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID participantId;

  private UUID questionId;
  private UUID submissionId;

  @Column(nullable = false)
  private int points;

  @Column(nullable = false, length = 48)
  private String entryType;

  @Column(length = 400)
  private String note;

  @Column(nullable = false)
  private Instant createdAt;

  protected ScoreLedger() { }

  public ScoreLedger(UUID activityId, UUID participantId, UUID questionId, UUID submissionId, int points,
      String entryType, String note) {
    this.activityId = activityId;
    this.participantId = participantId;
    this.questionId = questionId;
    this.submissionId = submissionId;
    this.points = points;
    this.entryType = entryType;
    this.note = note;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getParticipantId() { return participantId; }
  public UUID getQuestionId() { return questionId; }
  public UUID getSubmissionId() { return submissionId; }
  public int getPoints() { return points; }
  public String getEntryType() { return entryType; }
  public String getNote() { return note; }
  public Instant getCreatedAt() { return createdAt; }
}
