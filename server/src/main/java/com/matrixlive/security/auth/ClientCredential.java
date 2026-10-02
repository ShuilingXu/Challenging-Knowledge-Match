package com.matrixlive.security.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_credentials")
public class ClientCredential {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false, length = 24)
  private String kind;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false)
  private UUID subjectId;

  @Column(nullable = false)
  private Instant expiresAt;

  protected ClientCredential() {}

  public ClientCredential(
      UUID id, String tokenHash, String kind, UUID activityId, UUID subjectId, Instant expiresAt) {
    this.id = id;
    this.tokenHash = tokenHash;
    this.kind = kind;
    this.activityId = activityId;
    this.subjectId = subjectId;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public String getKind() {
    return kind;
  }

  public UUID getActivityId() {
    return activityId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
