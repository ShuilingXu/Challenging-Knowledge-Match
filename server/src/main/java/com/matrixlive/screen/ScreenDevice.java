package com.matrixlive.screen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screen_devices")
public class ScreenDevice {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, unique = true, length = 64)
  private String deviceTokenHash;

  private Integer viewportWidth;
  private Integer viewportHeight;
  private UUID currentTemplateId;

  @Column(nullable = false, length = 32)
  private String displayMode;

  @Column(nullable = false, columnDefinition = "text")
  private String displayPayloadJson;

  @Column(nullable = false)
  private int fontScale;

  @Column(nullable = false)
  private int volume;

  @Column(nullable = false)
  private int scrollPosition;

  @Column(nullable = false)
  private boolean autoScroll;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private Instant lastSeenAt;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected ScreenDevice() { }

  public ScreenDevice(UUID activityId, String name, String deviceTokenHash, Integer viewportWidth, Integer viewportHeight) {
    this.activityId = activityId;
    this.name = name;
    this.deviceTokenHash = deviceTokenHash;
    this.viewportWidth = viewportWidth;
    this.viewportHeight = viewportHeight;
    this.displayMode = ScreenDisplayMode.LOBBY.name();
    this.displayPayloadJson = "{}";
    this.fontScale = 100;
    this.volume = 70;
    this.scrollPosition = 0;
    this.autoScroll = false;
    this.status = "ONLINE";
    this.lastSeenAt = Instant.now();
    this.createdAt = this.lastSeenAt;
    this.updatedAt = this.lastSeenAt;
  }

  public void rename(String name) {
    this.name = name;
    touch();
  }

  public void rotatePairingToken(String deviceTokenHash) {
    this.deviceTokenHash = deviceTokenHash;
    touch();
  }

  public void updateDisplay(UUID templateId, ScreenDisplayMode displayMode, String displayPayloadJson) {
    this.currentTemplateId = templateId;
    this.displayMode = displayMode.name();
    this.displayPayloadJson = displayPayloadJson;
    this.scrollPosition = 0;
    touch();
  }

  public void updateSettings(Integer fontScale, Integer volume, Integer scrollPosition, Boolean autoScroll) {
    if (fontScale != null) this.fontScale = fontScale;
    if (volume != null) this.volume = volume;
    if (scrollPosition != null) this.scrollPosition = scrollPosition;
    if (autoScroll != null) this.autoScroll = autoScroll;
    touch();
  }

  public void heartbeat(Integer viewportWidth, Integer viewportHeight) {
    if (viewportWidth != null) this.viewportWidth = viewportWidth;
    if (viewportHeight != null) this.viewportHeight = viewportHeight;
    this.status = "ONLINE";
    this.lastSeenAt = Instant.now();
    this.updatedAt = this.lastSeenAt;
  }

  public void markOffline() {
    this.status = "OFFLINE";
    touch();
  }

  private void touch() { this.updatedAt = Instant.now(); }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getName() { return name; }
  public String getDeviceTokenHash() { return deviceTokenHash; }
  public Integer getViewportWidth() { return viewportWidth; }
  public Integer getViewportHeight() { return viewportHeight; }
  public UUID getCurrentTemplateId() { return currentTemplateId; }
  public String getDisplayMode() { return displayMode; }
  public String getDisplayPayloadJson() { return displayPayloadJson; }
  public int getFontScale() { return fontScale; }
  public int getVolume() { return volume; }
  public int getScrollPosition() { return scrollPosition; }
  public boolean isAutoScroll() { return autoScroll; }
  public String getStatus() { return status; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
