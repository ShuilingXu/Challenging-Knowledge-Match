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
@Table(name = "venues", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "code"}))
public class Venue {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 80)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private boolean enabled;

  private Integer capacity;
  private Instant createdAt;

  @Version
  private long version;

  protected Venue() { }

  public Venue(UUID activityId, String code, String name, Integer capacity) {
    this.activityId = activityId;
    this.code = code;
    this.name = name;
    this.capacity = capacity;
    this.enabled = true;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public boolean isEnabled() { return enabled; }
  public Integer getCapacity() { return capacity; }
  public Instant getCreatedAt() { return createdAt; }
  public long getVersion() { return version; }

  public void update(String name, Integer capacity, Boolean enabled) {
    if (name != null) this.name = name;
    if (capacity != null) this.capacity = capacity;
    if (enabled != null) this.enabled = enabled;
  }
}
