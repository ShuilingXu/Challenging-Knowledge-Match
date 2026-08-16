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
@Table(name = "participants", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "venue", "contact"}))
public class Participant {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 80)
  private String venue;

  @Column(nullable = false, length = 160)
  private String contact;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 180)
  private String organization;

  @Column(columnDefinition = "text")
  private String registrationData;

  @Column(nullable = false, length = 24)
  private String status;

  private int score;
  private Instant registeredAt;
  private Instant lastScoreAt;

  @Version
  private long version;

  protected Participant() { }

  public Participant(UUID activityId, String venue, String contact, String name, String organization) {
    this(activityId, venue, contact, name, organization, "{}");
  }

  public Participant(UUID activityId, String venue, String contact, String name, String organization, String registrationData) {
    this.activityId = activityId;
    this.venue = venue;
    this.contact = contact;
    this.name = name;
    this.organization = organization;
    this.registrationData = registrationData == null ? "{}" : registrationData;
    this.status = "ACTIVE";
    this.score = 0;
    this.registeredAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getVenue() { return venue; }
  public String getContact() { return contact; }
  public String getName() { return name; }
  public String getOrganization() { return organization; }
  public String getRegistrationData() { return registrationData; }
  public String getStatus() { return status; }
  public int getScore() { return score; }
  public Instant getRegisteredAt() { return registeredAt; }
  public Instant getLastScoreAt() { return lastScoreAt; }
  public long getVersion() { return version; }

  public void addScore(int points) {
    score += points;
    lastScoreAt = Instant.now();
  }

  public void updateProfile(String name, String contact, String organization, String registrationData, String venue) {
    if (name != null) this.name = name;
    if (contact != null) this.contact = contact;
    if (organization != null) this.organization = organization;
    if (registrationData != null) this.registrationData = registrationData;
    if (venue != null) this.venue = venue;
  }

  public void changeStatus(String status) { this.status = status; }
}
