package com.matrixlive.screen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screen_templates")
public class ScreenTemplate {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  private boolean preset;

  @Column(nullable = false, columnDefinition = "text")
  private String componentsJson;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected ScreenTemplate() { }

  public ScreenTemplate(UUID activityId, String name, String description, boolean preset, String componentsJson) {
    this.activityId = activityId;
    this.name = name;
    this.description = description;
    this.preset = preset;
    this.componentsJson = componentsJson;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(String name, String description, String componentsJson) {
    this.name = name;
    this.description = description;
    this.componentsJson = componentsJson;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public boolean isPreset() { return preset; }
  public String getComponentsJson() { return componentsJson; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
