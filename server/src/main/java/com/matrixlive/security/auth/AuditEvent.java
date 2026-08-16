package com.matrixlive.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_events_actor", columnList = "actorId,occurredAt"),
    @Index(name = "idx_audit_events_activity", columnList = "activityId,occurredAt")
})
public class AuditEvent {
  @Id
  @GeneratedValue
  private UUID id;
  @Column(nullable = false, length = 80)
  private String eventType;
  @Column(length = 40)
  private String actorType;
  private UUID actorId;
  private UUID activityId;
  @Column(nullable = false)
  private boolean success;
  @Column(length = 64)
  private String ipAddress;
  @Column(length = 512)
  private String userAgent;
  @Column(length = 1000)
  private String details;
  @Column(nullable = false)
  private Instant occurredAt;

  protected AuditEvent() { }

  public AuditEvent(String eventType, String actorType, UUID actorId, UUID activityId, boolean success,
      String ipAddress, String userAgent, String details) {
    this.eventType = eventType;
    this.actorType = actorType;
    this.actorId = actorId;
    this.activityId = activityId;
    this.success = success;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.details = details;
    this.occurredAt = Instant.now();
  }
}
