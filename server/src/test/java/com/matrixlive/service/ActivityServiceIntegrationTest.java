package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.matrixlive.api.ApiModels.CreateActivityRequest;
import com.matrixlive.api.ApiModels.ControlRequest;
import com.matrixlive.api.ApiModels.QuestionWriteRequest;
import com.matrixlive.api.ApiModels.RegisterParticipantRequest;
import com.matrixlive.api.ApiModels.SubmitAnswerRequest;
import com.matrixlive.domain.Question;
import com.matrixlive.repository.QuestionRepository;
import com.matrixlive.screen.ScreenDisplayMode;
import com.matrixlive.screen.ScreenService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActivityServiceIntegrationTest {
  @Autowired private ActivityService service;
  @Autowired private QuestionRepository questions;
  @Autowired private ScreenService screens;

  @Test
  void isolatesRegistrationAndReplaysIdempotentAnswers() {
    var activity = service.createActivity(new CreateActivityRequest("API 测试活动", "上海", Instant.now()));
    var participant = service.register(activity.id(), "south", new RegisterParticipantRequest("测试用户", "138 0000 2048", "QA"));
    assertThrows(DomainException.class, () -> service.register(activity.id(), "south",
        new RegisterParticipantRequest("测试用户", "13800002048", "QA")));

    Question question = questions.save(new Question(activity.id(), "MULTIPLE", "哪些原则可信？", "A|B|C|D", "A,B,D", 100));
    String idempotencyKey = UUID.randomUUID().toString();
    assertThrows(DomainException.class, () -> service.submitAnswer(activity.id(),
        new SubmitAnswerRequest(participant.id(), question.getId(), Set.of("A", "B", "D"), idempotencyKey)));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.getId(), 30));
    var first = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.getId(), Set.of("A", "B", "D"), idempotencyKey));
    var replay = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.getId(), Set.of("A", "B", "D"), idempotencyKey));

    assertEquals(100, first.awardedPoints());
    assertFalse(first.replayed());
    assertTrue(replay.replayed());
    assertEquals(first.submissionId(), replay.submissionId());
  }

  @Test
  void controlStatePublishesDeviceScopedQuestionAndScoreboardViews() {
    var activity = service.createActivity(new CreateActivityRequest("Screen control", "Shanghai", Instant.now()));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Which signal is live?",
        java.util.List.of("Alpha", "Beta"), Set.of("Alpha"), 100, 0, null, 40, true));
    var device = screens.registerDevice(activity.id(), new com.matrixlive.screen.ScreenModels.RegisterScreenDeviceRequest(
        "Main stage", 1920, 1080)).device();

    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 42));
    var questionDisplay = screens.currentDisplay(activity.id(), device.id());
    assertEquals(ScreenDisplayMode.QUESTION, questionDisplay.mode());
    assertEquals("Which signal is live?", questionDisplay.data().get("title"));
    assertEquals(42, questionDisplay.data().get("seconds"));

    service.control(activity.id(), new ControlRequest("SCOREBOARD", question.id(), 0));
    var boardDisplay = screens.currentDisplay(activity.id(), device.id());
    assertEquals(ScreenDisplayMode.SCOREBOARD, boardDisplay.mode());
    assertTrue(boardDisplay.data().containsKey("rows"));
  }
}
