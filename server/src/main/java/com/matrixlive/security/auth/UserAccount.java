package com.matrixlive.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String username;

  @Column(nullable = false, length = 100)
  private String displayName;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private UserRole systemRole;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private Instant createdAt;

  protected UserAccount() { }

  public UserAccount(String username, String displayName, String passwordHash, UserRole systemRole) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.systemRole = systemRole;
    this.enabled = true;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getUsername() { return username; }
  public String getDisplayName() { return displayName; }
  public String getPasswordHash() { return passwordHash; }
  public UserRole getSystemRole() { return systemRole; }
  public boolean isEnabled() { return enabled; }
  public Instant getCreatedAt() { return createdAt; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public void changeUsername(String username) { this.username = username; }
  public void changeDisplayName(String displayName) { this.displayName = displayName; }
  public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
}
