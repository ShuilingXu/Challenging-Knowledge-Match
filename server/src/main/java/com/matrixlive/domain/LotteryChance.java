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
@Table(name = "lottery_chances", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "participantId"}))
public class LotteryChance {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID participantId;

  @Column(nullable = false)
  private int remainingDraws;

  @Column(nullable = false)
  private int grantedDraws;

  @Column(length = 200)
  private String lastGrantReason;

  private Instant updatedAt;

  @Version
  private long version;

  protected LotteryChance() { }

  public LotteryChance(UUID activityId, UUID participantId, int draws, String reason) {
    this.activityId = activityId;
    this.participantId = participantId;
    this.remainingDraws = draws;
    this.grantedDraws = draws;
    this.lastGrantReason = reason;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getParticipantId() { return participantId; }
  public int getRemainingDraws() { return remainingDraws; }
  public int getGrantedDraws() { return grantedDraws; }
  public String getLastGrantReason() { return lastGrantReason; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public void grant(int draws, String reason) {
    remainingDraws += draws;
    grantedDraws += draws;
    lastGrantReason = reason;
    updatedAt = Instant.now();
  }

  public void consume() {
    if (remainingDraws <= 0) throw new IllegalStateException("No lottery chances remaining");
    remainingDraws--;
    updatedAt = Instant.now();
  }
}
