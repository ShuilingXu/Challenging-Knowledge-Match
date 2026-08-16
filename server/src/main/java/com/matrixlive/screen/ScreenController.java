package com.matrixlive.screen;

import static com.matrixlive.screen.ScreenModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities/{activityId}/screens")
public class ScreenController {
  private final ScreenService service;

  public ScreenController(ScreenService service) { this.service = service; }

  @GetMapping("/component-types")
  public List<ScreenComponentType> componentTypes(@PathVariable UUID activityId) {
    return service.componentTypes(activityId);
  }

  @GetMapping("/templates")
  public List<ScreenTemplateResponse> templates(@PathVariable UUID activityId) { return service.listTemplates(activityId); }

  @PostMapping("/templates")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public ScreenTemplateResponse createTemplate(@PathVariable UUID activityId, @Valid @RequestBody UpsertScreenTemplateRequest request) {
    return service.createTemplate(activityId, request);
  }

  @GetMapping("/templates/{templateId}")
  public ScreenTemplateResponse template(@PathVariable UUID activityId, @PathVariable UUID templateId) {
    return service.getTemplate(activityId, templateId);
  }

  @PutMapping("/templates/{templateId}")
  public ScreenTemplateResponse updateTemplate(@PathVariable UUID activityId, @PathVariable UUID templateId,
      @Valid @RequestBody UpsertScreenTemplateRequest request) {
    return service.updateTemplate(activityId, templateId, request);
  }

  @DeleteMapping("/templates/{templateId}")
  public ResponseEntity<Void> deleteTemplate(@PathVariable UUID activityId, @PathVariable UUID templateId) {
    service.deleteTemplate(activityId, templateId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/templates/{templateId}/apply")
  public List<ScreenDisplayResponse> applyTemplate(@PathVariable UUID activityId, @PathVariable UUID templateId,
      @Valid @RequestBody ApplyScreenTemplateRequest request) {
    return service.applyTemplate(activityId, templateId, request);
  }

  @GetMapping("/devices")
  public List<ScreenDeviceResponse> devices(@PathVariable UUID activityId) { return service.listDevices(activityId); }

  @PostMapping("/devices/register")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public ScreenDeviceRegistration registerDevice(@PathVariable UUID activityId,
      @Valid @RequestBody RegisterScreenDeviceRequest request) {
    return service.registerDevice(activityId, request);
  }

  @PostMapping("/devices/{deviceId}/session")
  public ScreenDeviceSession exchangePairingToken(@PathVariable UUID activityId, @PathVariable UUID deviceId,
      @RequestHeader(name = "X-Screen-Pairing", required = false) String pairingToken) {
    return service.exchangePairingToken(activityId, deviceId, pairingToken);
  }

  @GetMapping("/devices/{deviceId}")
  public ScreenDeviceResponse device(@PathVariable UUID activityId, @PathVariable UUID deviceId) {
    return service.getDevice(activityId, deviceId);
  }

  @GetMapping("/devices/{deviceId}/display")
  public ScreenDisplayResponse display(@PathVariable UUID activityId, @PathVariable UUID deviceId) {
    return service.currentDisplay(activityId, deviceId);
  }

  @PatchMapping("/devices/{deviceId}")
  public ScreenDeviceResponse renameDevice(@PathVariable UUID activityId, @PathVariable UUID deviceId,
      @Valid @RequestBody RenameScreenDeviceRequest request) {
    return service.renameDevice(activityId, deviceId, request);
  }

  @PostMapping("/devices/{deviceId}/pairing-token")
  public ScreenDeviceRegistration rotatePairingToken(@PathVariable UUID activityId, @PathVariable UUID deviceId) {
    return service.rotatePairingToken(activityId, deviceId);
  }

  @PutMapping("/devices/{deviceId}/display")
  public ScreenDisplayResponse setDisplay(@PathVariable UUID activityId, @PathVariable UUID deviceId,
      @Valid @RequestBody SetScreenDisplayRequest request) {
    return service.setDisplay(activityId, deviceId, request);
  }

  @PutMapping("/devices/{deviceId}/settings")
  public ScreenDeviceResponse updateSettings(@PathVariable UUID activityId, @PathVariable UUID deviceId,
      @Valid @RequestBody UpdateScreenSettingsRequest request) {
    return service.updateSettings(activityId, deviceId, request);
  }

  @PostMapping("/devices/{deviceId}/offline")
  public ScreenDeviceResponse markOffline(@PathVariable UUID activityId, @PathVariable UUID deviceId) {
    return service.markOffline(activityId, deviceId);
  }
}
