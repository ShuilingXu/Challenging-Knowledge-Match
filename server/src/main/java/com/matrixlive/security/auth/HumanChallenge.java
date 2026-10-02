package com.matrixlive.security.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "human_challenges")
public class HumanChallenge {
  @Id private UUID id;

  @Column(nullable = false, length = 64)
  private String answerHash;

  @Column(nullable = false, length = 64)
  private String ip;

  @Column(length = 64)
  private String contactHash;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private boolean used;

  protected HumanChallenge() {}

  public HumanChallenge(UUID id, String answerHash, String ip) {
    this.id = id;
    this.answerHash = answerHash;
    this.ip = ip;
    createdAt = Instant.now();
    expiresAt = createdAt.plusSeconds(120);
  }

  public boolean matches(String ip, String answer) {
    return !used
        && expiresAt.isAfter(Instant.now())
        && this.ip.equals(ip)
        && answerHash.equals(AuthService.hash(answer));
  }

  public void consume(String contact) {
    used = true;
    contactHash = contact;
  }
}
