package com.matrixlive.screen;

import static com.matrixlive.screen.ScreenModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScreenSubmissionCountIntegrationTest {
  @Autowired private ScreenService screens;
  @Autowired private ActivityRepository activities;

  @Test
  void refreshesOnlyScreensShowingTheSubmittedQuestionAndPreservesDisplaySettings() {
    UUID activityId = activities.save(new Activity("Submission screens", "Shanghai", "LIVE", Instant.now())).getId();
    UUID questionId = UUID.randomUUID();
    UUID templateId = screens.listTemplates(activityId).getFirst().id();
    UUID questionDevice = register(activityId, "Question");
    UUID resultDevice = register(activityId, "Result");
    UUID otherQuestionDevice = register(activityId, "Other question");
    UUID announcementDevice = register(activityId, "Announcement");
    screens.setDisplay(activityId, questionDevice, new SetScreenDisplayRequest(ScreenDisplayMode.QUESTION, templateId,
        Map.of("questionId", questionId, "question", "Keep this question", "submittedCount", 0)));
    screens.updateSettings(activityId, questionDevice, new UpdateScreenSettingsRequest(125, 35, 300, true));
    screens.setDisplay(activityId, resultDevice, new SetScreenDisplayRequest(ScreenDisplayMode.RESULT, null,
        Map.of("questionId", questionId, "correctOptions", "A", "submittedCount", 0)));
    screens.setDisplay(activityId, otherQuestionDevice, new SetScreenDisplayRequest(ScreenDisplayMode.QUESTION, null,
        Map.of("questionId", UUID.randomUUID(), "submittedCount", 2)));
    screens.setDisplay(activityId, announcementDevice, new SetScreenDisplayRequest(ScreenDisplayMode.ANNOUNCEMENT, null,
        Map.of("questionId", questionId, "message", "Keep announcement")));

    screens.refreshQuestionSubmissionCount(activityId, questionId, 7);

    ScreenDisplayResponse question = screens.currentDisplay(activityId, questionDevice);
    assertEquals(ScreenDisplayMode.QUESTION, question.mode());
    assertEquals(templateId, question.templateId());
    assertEquals("Keep this question", question.data().get("question"));
    assertEquals(7, ((Number) question.data().get("submittedCount")).intValue());
    assertEquals(125, question.fontScale());
    assertEquals(35, question.volume());
    assertEquals(300, question.scrollPosition());
    assertEquals(true, question.autoScroll());
    ScreenDisplayResponse result = screens.currentDisplay(activityId, resultDevice);
    assertEquals(ScreenDisplayMode.RESULT, result.mode());
    assertEquals("A", result.data().get("correctOptions"));
    assertEquals(7, ((Number) result.data().get("submittedCount")).intValue());
    assertEquals(2, screens.currentDisplay(activityId, otherQuestionDevice).data().get("submittedCount"));
    assertFalse(screens.currentDisplay(activityId, announcementDevice).data().containsKey("submittedCount"));
  }

  private UUID register(UUID activityId, String name) {
    return screens.registerDevice(activityId, new RegisterScreenDeviceRequest(name, 1920, 1080, null)).device().id();
  }
}
