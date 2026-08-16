package com.matrixlive.screen;

import static com.matrixlive.screen.ScreenModels.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities/{activityId}/screens/devices/{deviceId}")
public class ScreenDeviceController {
  private final ScreenService service;

  public ScreenDeviceController(ScreenService service) { this.service = service; }

  @GetMapping("/state")
  public ScreenDisplayResponse currentDisplay(@PathVariable UUID activityId, @PathVariable UUID deviceId) {
    return service.currentDisplay(activityId, deviceId);
  }

  @PostMapping("/heartbeat")
  public ScreenDeviceResponse heartbeat(@PathVariable UUID activityId, @PathVariable UUID deviceId,
      @Valid @RequestBody(required = false) ScreenHeartbeatRequest request) {
    return service.heartbeat(activityId, deviceId, request == null ? new ScreenHeartbeatRequest(null, null) : request);
  }
}
