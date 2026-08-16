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
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_user_id", columnList = "userId"),
    @Index(name = "idx_refresh_tokens_family_id", columnList = "familyId")
})
public class RefreshToken {
  @Id
  @GeneratedValue
  private UUID id;
  @Column(nullable = false)
  private UUID userId;
  @Column(nullable = false)
  private UUID familyId;
  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;
  @Column(nullable = false)
  private Instant expiresAt;
  private Instant revokedAt;
  @Column(length = 64)
  private String replacedByHash;
  @Column(nullable = false)
  private Instant createdAt;
  @Column(length = 64)
  private String ipAddress;
  @Column(length = 512)
  private String userAgent;

  protected RefreshToken() { }

  public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, String ipAddress, String userAgent) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getFamilyId() { return familyId; }
  public String getTokenHash() { return tokenHash; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
  public void revoke(String replacementHash) { this.revokedAt = Instant.now(); this.replacedByHash = replacementHash; }
}
