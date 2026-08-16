package com.matrixlive.security;

import com.matrixlive.security.auth.UserRole;
import java.time.Instant;
import java.util.UUID;

/** Principal populated solely from a verified, signed access token. */
public record AuthenticatedPrincipal(
    UUID tokenId,
    PrincipalKind kind,
    UUID userId,
    UUID participantId,
    UUID deviceId,
    UUID activityId,
    UserRole role,
    String username,
    Instant expiresAt) {

  public boolean isSystemAdmin() { return role == UserRole.SYSTEM_ADMIN; }
  public boolean isParticipant() { return kind == PrincipalKind.PARTICIPANT; }
  public boolean isScreenDevice() { return kind == PrincipalKind.SCREEN_DEVICE; }
}
