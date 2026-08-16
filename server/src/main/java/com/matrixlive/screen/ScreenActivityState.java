package com.matrixlive.screen;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "screen_activity_states")
public class ScreenActivityState {
  @Id
  private UUID activityId;
  private boolean presetsInitialized;

  protected ScreenActivityState() { }

  public ScreenActivityState(UUID activityId) {
    this.activityId = activityId;
    this.presetsInitialized = false;
  }

  public boolean isPresetsInitialized() { return presetsInitialized; }
  public void markPresetsInitialized() { this.presetsInitialized = true; }
}
