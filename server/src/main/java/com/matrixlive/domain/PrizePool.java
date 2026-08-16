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
@Table(name = "prize_pools", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "code"}))
public class PrizePool {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 80)
  private String code;

  @Column(nullable = false, length = 180)
  private String name;

  @Column(nullable = false, length = 32)
  private String purpose;

  @Column(nullable = false, length = 32)
  private String deliveryType;

  @Column(columnDefinition = "text")
  private String description;

  @Column(length = 1024)
  private String redemptionUrl;

  @Column(nullable = false)
  private int totalQuantity;

  @Column(nullable = false)
  private int claimedQuantity;

  @Column(nullable = false)
  private int minScore;

  @Column(nullable = false)
  private int drawWeight;

  private Integer rankFrom;
  private Integer rankTo;

  @Column(nullable = false)
  private boolean enabled;

  private Instant createdAt;
  private Instant updatedAt;

  @Version
  private long version;

  protected PrizePool() { }

  public PrizePool(UUID activityId, String code, String name, String purpose, String deliveryType,
      String description, String redemptionUrl, int totalQuantity, int minScore, int drawWeight) {
    this(activityId, code, name, purpose, deliveryType, description, redemptionUrl, totalQuantity, minScore,
        drawWeight, null, null);
  }

  public PrizePool(UUID activityId, String code, String name, String purpose, String deliveryType,
      String description, String redemptionUrl, int totalQuantity, int minScore, int drawWeight,
      Integer rankFrom, Integer rankTo) {
    this.activityId = activityId;
    this.code = code;
    this.name = name;
    this.purpose = purpose;
    this.deliveryType = deliveryType;
    this.description = description;
    this.redemptionUrl = redemptionUrl;
    this.totalQuantity = totalQuantity;
    this.claimedQuantity = 0;
    this.minScore = minScore;
    this.drawWeight = drawWeight;
    this.rankFrom = rankFrom;
    this.rankTo = rankTo;
    this.enabled = true;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getPurpose() { return purpose; }
  public String getDeliveryType() { return deliveryType; }
  public String getDescription() { return description; }
  public String getRedemptionUrl() { return redemptionUrl; }
  public int getTotalQuantity() { return totalQuantity; }
  public int getClaimedQuantity() { return claimedQuantity; }
  public int getRemainingQuantity() { return Math.max(0, totalQuantity - claimedQuantity); }
  public int getMinScore() { return minScore; }
  public int getDrawWeight() { return drawWeight; }
  public Integer getRankFrom() { return rankFrom; }
  public Integer getRankTo() { return rankTo; }
  public boolean isEnabled() { return enabled; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public boolean isAvailableFor(int score) {
    return enabled && getRemainingQuantity() > 0 && score >= minScore;
  }

  public void claim() {
    if (getRemainingQuantity() <= 0) throw new IllegalStateException("Prize pool is exhausted");
    claimedQuantity++;
    updatedAt = Instant.now();
  }

  public void release() {
    if (claimedQuantity > 0) claimedQuantity--;
    updatedAt = Instant.now();
  }

  public void update(String name, String purpose, String deliveryType, String description, String redemptionUrl,
      Integer totalQuantity, Integer minScore, Integer drawWeight, Integer rankFrom, Integer rankTo, Boolean enabled) {
    if (name != null) this.name = name;
    if (purpose != null) this.purpose = purpose;
    if (deliveryType != null) this.deliveryType = deliveryType;
    if (description != null) this.description = description;
    if (redemptionUrl != null) this.redemptionUrl = redemptionUrl;
    if (totalQuantity != null) this.totalQuantity = totalQuantity;
    if (minScore != null) this.minScore = minScore;
    if (drawWeight != null) this.drawWeight = drawWeight;
    if (rankFrom != null) this.rankFrom = rankFrom;
    if (rankTo != null) this.rankTo = rankTo;
    if (enabled != null) this.enabled = enabled;
    updatedAt = Instant.now();
  }
}
