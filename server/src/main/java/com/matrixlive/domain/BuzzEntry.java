package com.matrixlive.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buzz_entries")
public class BuzzEntry {
  @Id @GeneratedValue private UUID id;
  @Column(nullable = false) private UUID activityId;
  @Column(nullable = false) private UUID questionId;
  @Column(nullable = false) private UUID participantId;
  @Column(nullable = false) private int responseRank;
  @Column(nullable = false) private Instant buzzedAt;
  private Integer awardedPoints;
  private Boolean correct;
  @Column(length = 1000) private String feedback;

  protected BuzzEntry() { }
  public BuzzEntry(UUID activityId, UUID questionId, UUID participantId, int rank) {
    this.activityId = activityId;
    this.questionId = questionId;
    this.participantId = participantId;
    this.responseRank = rank;
    this.buzzedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }
  public UUID getParticipantId() { return participantId; }
  public int getResponseRank() { return responseRank; }
  public Instant getBuzzedAt() { return buzzedAt; }
  public Integer getAwardedPoints() { return awardedPoints; }
  public Boolean getCorrect() { return correct; }
  public String getFeedback() { return feedback; }
  public void grade(boolean correct, int points, String feedback) { this.correct = correct; this.awardedPoints = points; this.feedback = feedback; }
}
