package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prize_awards")
public class PrizeAward {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID participantId;

  private UUID prizePoolId;

  @Column(nullable = false, length = 180)
  private String prizeName;

  @Column(nullable = false, length = 32)
  private String deliveryType;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(length = 128, unique = true)
  private String redemptionCode;

  @Column(length = 1024)
  private String redemptionUrl;

  @Column(length = 600)
  private String fulfillmentNote;

  private Instant awardedAt;
  private Instant redeemedAt;
  private Instant voidedAt;
  private String redeemedBy;

  @Version
  private long version;

  protected PrizeAward() { }

  public PrizeAward(UUID activityId, UUID participantId, String prizeName, String deliveryType, String status,
      String redemptionCode) {
    this(activityId, participantId, null, prizeName, deliveryType, status, redemptionCode, null, null);
  }

  public PrizeAward(UUID activityId, UUID participantId, UUID prizePoolId, String prizeName, String deliveryType,
      String status, String redemptionCode, String redemptionUrl, String fulfillmentNote) {
    this.activityId = activityId;
    this.participantId = participantId;
    this.prizePoolId = prizePoolId;
    this.prizeName = prizeName;
    this.deliveryType = deliveryType;
    this.status = status;
    this.redemptionCode = redemptionCode;
    this.redemptionUrl = redemptionUrl;
    this.fulfillmentNote = fulfillmentNote;
    this.awardedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getParticipantId() { return participantId; }
  public UUID getPrizePoolId() { return prizePoolId; }
  public String getPrizeName() { return prizeName; }
  public String getDeliveryType() { return deliveryType; }
  public String getStatus() { return status; }
  public String getRedemptionCode() { return redemptionCode; }
  public String getRedemptionUrl() { return redemptionUrl; }
  public String getFulfillmentNote() { return fulfillmentNote; }
  public Instant getAwardedAt() { return awardedAt; }
  public Instant getRedeemedAt() { return redeemedAt; }
  public Instant getVoidedAt() { return voidedAt; }
  public String getRedeemedBy() { return redeemedBy; }
  public long getVersion() { return version; }

  public void redeem(String operator) {
    if ("VOID".equals(status)) throw new IllegalStateException("Voided award cannot be redeemed");
    status = "REDEEMED";
    redeemedAt = Instant.now();
    redeemedBy = operator;
  }

  public void reverseRedemption() {
    if (!"REDEEMED".equals(status)) throw new IllegalStateException("Award has not been redeemed");
    status = "PENDING";
    redeemedAt = null;
    redeemedBy = null;
  }

  public void voidAward(String note) {
    if ("REDEEMED".equals(status)) throw new IllegalStateException("Redeemed award cannot be voided");
    status = "VOID";
    fulfillmentNote = note;
    voidedAt = Instant.now();
  }
}
