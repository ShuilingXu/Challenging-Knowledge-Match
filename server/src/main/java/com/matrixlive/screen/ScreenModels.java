package com.matrixlive.screen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScreenModels {
  private ScreenModels() { }

  public record ScreenComponent(
      @NotBlank @Size(max = 80) String id,
      @NotNull ScreenComponentType type,
      Map<String, Object> config) { }

  public record UpsertScreenTemplateRequest(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String description,
      @NotNull @Size(min = 1, max = 30) List<@Valid ScreenComponent> components) { }

  public record RegisterScreenDeviceRequest(
      @NotBlank @Size(max = 120) String name,
      @Min(1) @Max(20000) Integer viewportWidth,
      @Min(1) @Max(20000) Integer viewportHeight) { }

  public record RenameScreenDeviceRequest(@NotBlank @Size(max = 120) String name) { }

  public record ApplyScreenTemplateRequest(
      @Size(max = 100) List<UUID> deviceIds,
      Map<String, Object> overrides) { }

  public record SetScreenDisplayRequest(
      @NotNull ScreenDisplayMode mode,
      UUID templateId,
      Map<String, Object> data) { }

  public record UpdateScreenSettingsRequest(
      @Min(50) @Max(200) Integer fontScale,
      @Min(0) @Max(100) Integer volume,
      @Min(0) @Max(100000) Integer scrollPosition,
      Boolean autoScroll) { }

  public record ScreenHeartbeatRequest(
      @Min(1) @Max(20000) Integer viewportWidth,
      @Min(1) @Max(20000) Integer viewportHeight) { }

  public record ScreenTemplateResponse(
      UUID id,
      UUID activityId,
      String name,
      String description,
      boolean preset,
      List<ScreenComponent> components,
      Instant createdAt,
      Instant updatedAt) { }

  public record ScreenDeviceResponse(
      UUID id,
      UUID activityId,
      String name,
      Integer viewportWidth,
      Integer viewportHeight,
      UUID currentTemplateId,
      ScreenDisplayMode displayMode,
      int fontScale,
      int volume,
      int scrollPosition,
      boolean autoScroll,
      String status,
      Instant lastSeenAt,
      Instant createdAt,
      Instant updatedAt) { }

  public record ScreenDeviceRegistration(
      ScreenDeviceResponse device,
      String pairingToken) { }

  public record ScreenDeviceSession(
      ScreenDeviceResponse device,
      String accessToken,
      String tokenType,
      Instant expiresAt) { }

  public record ScreenDisplayResponse(
      UUID deviceId,
      UUID activityId,
      String deviceName,
      UUID templateId,
      ScreenTemplateResponse template,
      ScreenDisplayMode mode,
      Map<String, Object> data,
      int fontScale,
      int volume,
      int scrollPosition,
      boolean autoScroll,
      Instant updatedAt) { }

  public record ScreenEvent(String type, String scope, Object payload, Instant sentAt) { }
}
