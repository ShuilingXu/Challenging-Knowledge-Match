package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lottery_draws", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "idempotencyKey"}))
public class LotteryDraw {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID participantId;

  @Column(nullable = false)
  private UUID prizePoolId;

  @Column(nullable = false)
  private UUID prizeAwardId;

  @Column(nullable = false, length = 160)
  private String idempotencyKey;

  @Column(nullable = false)
  private Instant drawnAt;

  protected LotteryDraw() { }

  public LotteryDraw(UUID activityId, UUID participantId, UUID prizePoolId, UUID prizeAwardId,
      String idempotencyKey) {
    this.activityId = activityId;
    this.participantId = participantId;
    this.prizePoolId = prizePoolId;
    this.prizeAwardId = prizeAwardId;
    this.idempotencyKey = idempotencyKey;
    this.drawnAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getParticipantId() { return participantId; }
  public UUID getPrizePoolId() { return prizePoolId; }
  public UUID getPrizeAwardId() { return prizeAwardId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public Instant getDrawnAt() { return drawnAt; }
}
