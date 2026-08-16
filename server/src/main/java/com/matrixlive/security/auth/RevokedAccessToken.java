package com.matrixlive.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revoked_access_tokens")
public class RevokedAccessToken {
  @Id
  @Column(length = 64)
  private String tokenId;
  @Column(nullable = false)
  private Instant expiresAt;
  @Column(nullable = false)
  private UUID userId;
  @Column(nullable = false)
  private Instant revokedAt;

  protected RevokedAccessToken() { }

  public RevokedAccessToken(String tokenId, Instant expiresAt, UUID userId) {
    this.tokenId = tokenId;
    this.expiresAt = expiresAt;
    this.userId = userId;
    this.revokedAt = Instant.now();
  }

  public Instant getExpiresAt() { return expiresAt; }
}
