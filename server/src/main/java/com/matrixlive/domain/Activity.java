package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 80)
  private String city;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(columnDefinition = "text")
  private String description;

  @Column(length = 160)
  private String clientDisplayName;

  @Column(length = 16)
  private String clientThemeColor;

  @Column(length = 1024)
  private String clientHeroImageUrl;

  @Column(length = 1024)
  private String clientBackgroundImageUrl;

  private Instant startsAt;
  private Instant endsAt;
  private Instant createdAt;
  private Instant updatedAt;

  @Version
  private long version;

  protected Activity() { }

  public Activity(String name, String city, String status, Instant startsAt) {
    this(name, city, status, startsAt, null, null);
  }

  public Activity(String name, String city, String status, Instant startsAt, Instant endsAt, String description) {
    this.name = name;
    this.city = city;
    this.status = status;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.description = description;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getCity() { return city; }
  public String getStatus() { return status; }
  public String getDescription() { return description; }
  public String getClientDisplayName() { return clientDisplayName; }
  public String getClientThemeColor() { return clientThemeColor; }
  public String getClientHeroImageUrl() { return clientHeroImageUrl; }
  public String getClientBackgroundImageUrl() { return clientBackgroundImageUrl; }
  public Instant getStartsAt() { return startsAt; }
  public Instant getEndsAt() { return endsAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public void update(String name, String city, Instant startsAt, Instant endsAt, String description) {
    if (name != null) this.name = name;
    if (city != null) this.city = city;
    if (startsAt != null) this.startsAt = startsAt;
    this.endsAt = endsAt;
    if (description != null) this.description = description;
  }

  public void updateClientBrand(String clientDisplayName, String clientThemeColor, String clientHeroImageUrl,
      String clientBackgroundImageUrl) {
    this.clientDisplayName = clientDisplayName;
    this.clientThemeColor = clientThemeColor;
    this.clientHeroImageUrl = clientHeroImageUrl;
    this.clientBackgroundImageUrl = clientBackgroundImageUrl;
  }

  public void changeStatus(String status) { this.status = status; }
}
