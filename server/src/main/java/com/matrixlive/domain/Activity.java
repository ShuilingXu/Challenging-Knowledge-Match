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

  @Column(name = "parent_activity_id")
  private UUID parentActivityId;

  @Column(name = "activity_type", nullable = false, length = 24)
  private String activityType;

  @Column(name = "active_question_set_id")
  private UUID activeQuestionSetId;

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

  /** Answer scoring configuration. Percentages are applied to each question's full score. */
  @Column(name = "scoring_mode", nullable = false, length = 16)
  private String scoringMode = "SIMPLE";
  @Column(name = "correct_score_percent", nullable = false)
  private int correctScorePercent = 100;
  @Column(name = "incorrect_score_percent", nullable = false)
  private int incorrectScorePercent = 0;
  @Column(name = "scoring_rules", columnDefinition = "text")
  private String scoringRules = "{}";

  private Instant startsAt;
  private Instant endsAt;
  private Instant createdAt;
  private Instant updatedAt;

  @Column(nullable = false, length = 32)
  private String controlStage = "LOBBY";
  private UUID controlQuestionId;
  private int controlSeconds;
  private Instant controlUpdatedAt;
  private Instant questionOpenedAt;

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
    this.activityType = "EVENT";
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
  public UUID getParentActivityId() { return parentActivityId; }
  public String getActivityType() { return activityType; }
  public UUID getActiveQuestionSetId() { return activeQuestionSetId; }
  public String getDescription() { return description; }
  public String getClientDisplayName() { return clientDisplayName; }
  public String getClientThemeColor() { return clientThemeColor; }
  public String getClientHeroImageUrl() { return clientHeroImageUrl; }
  public String getClientBackgroundImageUrl() { return clientBackgroundImageUrl; }
  public String getScoringMode() { return scoringMode; }
  public int getCorrectScorePercent() { return correctScorePercent; }
  public int getIncorrectScorePercent() { return incorrectScorePercent; }
  public String getScoringRules() { return scoringRules; }
  public Instant getStartsAt() { return startsAt; }
  public Instant getEndsAt() { return endsAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
  public String getControlStage() { return controlStage; }
  public UUID getControlQuestionId() { return controlQuestionId; }
  public int getControlSeconds() { return controlSeconds; }
  public Instant getControlUpdatedAt() { return controlUpdatedAt; }
  public Instant getQuestionOpenedAt() { return questionOpenedAt; }

  public void updateControl(String stage, UUID questionId, int seconds, Instant now) {
    controlStage = stage;
    controlQuestionId = questionId;
    controlSeconds = seconds;
    controlUpdatedAt = now;
    if ("QUESTION_OPEN".equals(stage)) questionOpenedAt = now;
  }

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

  public void updateScoring(String mode, int correctPercent, int incorrectPercent, String rules) {
    this.scoringMode = mode;
    this.correctScorePercent = correctPercent;
    this.incorrectScorePercent = incorrectPercent;
    this.scoringRules = rules == null ? "{}" : rules;
  }

  public void changeStatus(String status) { this.status = status; }

  public void configureHierarchy(UUID parentActivityId, String activityType) {
    this.parentActivityId = parentActivityId;
    this.activityType = activityType == null || activityType.isBlank() ? "EVENT" : activityType;
  }

  public void activateQuestionSet(UUID questionSetId) { this.activeQuestionSetId = questionSetId; }
}
