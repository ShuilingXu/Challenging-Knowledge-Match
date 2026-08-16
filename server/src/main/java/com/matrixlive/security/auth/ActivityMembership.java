package com.matrixlive.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "userId"}))
public class ActivityMembership {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private UserRole role;

  @Column(nullable = false)
  private Instant createdAt;

  protected ActivityMembership() { }

  public ActivityMembership(UUID activityId, UUID userId, UserRole role) {
    if (role != UserRole.ACTIVITY_ADMIN && role != UserRole.STAFF) {
      throw new IllegalArgumentException("Activity membership must be ACTIVITY_ADMIN or STAFF");
    }
    this.activityId = activityId;
    this.userId = userId;
    this.role = role;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public UUID getUserId() { return userId; }
  public UserRole getRole() { return role; }
  public Instant getCreatedAt() { return createdAt; }
  public void changeRole(UserRole role) {
    if (role != UserRole.ACTIVITY_ADMIN && role != UserRole.STAFF) {
      throw new IllegalArgumentException("Activity membership must be ACTIVITY_ADMIN or STAFF");
    }
    this.role = role;
  }
}
