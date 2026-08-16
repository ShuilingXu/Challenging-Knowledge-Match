package com.matrixlive.security.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class AuthModels {
  private AuthModels() { }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
  public record RefreshRequest(String refreshToken) { }
  public record LogoutRequest(String refreshToken) { }
  public record AccessTokenResponse(String accessToken, String tokenType, Instant expiresAt,
                                    UUID userId, String username, String displayName, String systemRole) { }
  public record ParticipantTokenRequest(@NotNull UUID activityId, @NotBlank String venue, @NotBlank String contact) { }
  public record ParticipantTokenResponse(String accessToken, String tokenType, Instant expiresAt,
                                         UUID participantId, UUID activityId) { }
  public record CurrentPrincipalResponse(String kind, UUID userId, UUID participantId, UUID activityId,
                                         String username, String role, Instant expiresAt) { }
  public record CreateUserRequest(@NotBlank String username, @NotBlank String displayName, @NotBlank String password,
                                  UserRole systemRole) { }
  public record UpdateUserStatusRequest(boolean enabled) { }
  public record UserResponse(UUID id, String username, String displayName, String systemRole,
                             boolean enabled, Instant createdAt) { }
  public record MembershipRequest(@NotNull UUID userId, @NotNull UserRole role) { }
  public record CreateActivityMemberRequest(@NotBlank String username, @NotBlank String displayName,
      @NotBlank String password, @NotNull UserRole role) { }
  public record MembershipResponse(UUID id, UUID activityId, UUID userId, String username, String displayName,
                                   String role, Instant createdAt) { }
}
