package com.matrixlive.screen;

import static com.matrixlive.screen.ScreenModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.service.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScreenServiceIntegrationTest {
  @Autowired private ScreenService screens;
  @Autowired private ActivityRepository activities;

  @Test
  void managesTemplatesAndSynchronizesDeviceDisplayState() {
    Activity activity = activities.save(new Activity("Screen Test", "Shanghai", "LIVE", Instant.now()));

    List<ScreenTemplateResponse> presets = screens.listTemplates(activity.getId());
    assertEquals(4, presets.size());
    assertTrue(presets.stream().allMatch(ScreenTemplateResponse::preset));

    ScreenTemplateResponse template = screens.createTemplate(activity.getId(), new UpsertScreenTemplateRequest(
        "Sponsor message", "A reusable venue notice", List.of(
            new ScreenComponent("background", ScreenComponentType.BACKGROUND, Map.of("color", "#102030")),
            new ScreenComponent("headline", ScreenComponentType.TEXT, Map.of("text", "Welcome to the live challenge")),
            new ScreenComponent("poster", ScreenComponentType.IMAGE, Map.of("url", "https://cdn.example.test/poster.png")))));
    assertNotEquals(null, template.id());

    ScreenDeviceRegistration registration = screens.registerDevice(activity.getId(),
        new RegisterScreenDeviceRequest("South Hall", 1920, 1080));
    assertTrue(registration.pairingToken().length() > 30);
    assertEquals(ScreenDisplayMode.TEMPLATE, registration.device().displayMode());

    ScreenDisplayResponse applied = screens.applyTemplate(activity.getId(), template.id(),
        new ApplyScreenTemplateRequest(List.of(registration.device().id()), Map.of("headline", "Doors are open"))).getFirst();
    assertEquals(template.id(), applied.templateId());
    assertEquals(ScreenDisplayMode.TEMPLATE, applied.mode());
    assertEquals("Doors are open", ((Map<?, ?>) applied.data().get("overrides")).get("headline"));

    ScreenDeviceResponse tuned = screens.updateSettings(activity.getId(), registration.device().id(),
        new UpdateScreenSettingsRequest(130, 42, 240, true));
    assertEquals(130, tuned.fontScale());
    assertEquals(42, tuned.volume());
    assertEquals(240, tuned.scrollPosition());
    assertTrue(tuned.autoScroll());

    ScreenDeviceSession session = screens.exchangePairingToken(activity.getId(), registration.device().id(), registration.pairingToken());
    assertTrue(session.accessToken().length() > 50);
    assertThrows(DomainException.class,
        () -> screens.exchangePairingToken(activity.getId(), registration.device().id(), registration.pairingToken()));
    ScreenDisplayResponse deviceView = screens.currentDisplay(activity.getId(), registration.device().id());
    assertEquals("South Hall", deviceView.deviceName());
    assertEquals(template.id(), deviceView.templateId());
    assertThrows(DomainException.class,
        () -> screens.exchangePairingToken(activity.getId(), registration.device().id(), "not-the-pairing-token"));
  }

  @Test
  void rejectsTemplatesWithInvalidInteractiveComponents() {
    Activity activity = activities.save(new Activity("Component Test", "Guangzhou", "DRAFT", Instant.now()));
    UpsertScreenTemplateRequest invalid = new UpsertScreenTemplateRequest("Broken image", null,
        List.of(new ScreenComponent("broken", ScreenComponentType.IMAGE, Map.of())));

    DomainException exception = assertThrows(DomainException.class,
        () -> screens.createTemplate(activity.getId(), invalid));
    assertEquals(400, exception.getStatus().value());
  }
}
