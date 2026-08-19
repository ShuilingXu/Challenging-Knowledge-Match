package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.matrixlive.api.ApiModels.CreateActivityRequest;
import com.matrixlive.api.ApiModels.ControlRequest;
import com.matrixlive.api.ApiModels.QuestionWriteRequest;
import com.matrixlive.api.ApiModels.QuestionSetRequest;
import com.matrixlive.api.ApiModels.RegisterParticipantRequest;
import com.matrixlive.api.ApiModels.SubmitAnswerRequest;
import com.matrixlive.api.ApiModels.VenueRequest;
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
    service.createVenue(activity.id(), new VenueRequest("south", "South Hall", 20, true));
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

    var stats = service.questionResponseStats(activity.id(), question.getId());
    assertEquals(1, stats.eligibleParticipantCount());
    assertEquals(1, stats.submittedCount());
    assertEquals(0, stats.unansweredCount());
    assertEquals(1, stats.correctCount());
    assertEquals("测试用户", stats.submissions().getFirst().participantName());
    assertEquals(1, stats.submissions().getFirst().responseRank());
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

  @Test
  void editsQuestionsAndActivatesAnOrderedQuestionSetForRuntime() {
    var activity = service.createActivity(new CreateActivityRequest("Question set", "Shanghai", Instant.now()));
    var first = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "First", java.util.List.of("A", "B"),
        Set.of("A"), 100, 0, null, 40, true));
    var second = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Second", java.util.List.of("A", "B"),
        Set.of("B"), 100, 1, null, 40, true));

    service.updateQuestion(activity.id(), first.id(), new QuestionWriteRequest("SINGLE", "First with media",
        java.util.List.of("A", "B"), Set.of("B"), 80, 0, "https://cdn.example.test/first.png", 40, true));
    var edited = service.updateQuestion(activity.id(), first.id(), new QuestionWriteRequest("SINGLE", "First edited",
        java.util.List.of("A", "B"), Set.of("B"), 80, 0, "", 40, true));
    assertEquals("First edited", edited.title());
    assertEquals(80, edited.fullScore());
    assertEquals("", edited.mediaUrl());

    var set = service.createQuestionSet(activity.id(), new QuestionSetRequest("Final round", "Ordered runtime set",
        java.util.List.of(second.id(), first.id()), false));
    assertEquals(java.util.List.of(second.id(), first.id()), set.items().stream().map(item -> item.questionId()).toList());

    var reordered = service.updateQuestionSet(activity.id(), set.id(), new QuestionSetRequest("Final round",
        "Reordered runtime set", java.util.List.of(first.id(), second.id()), false));
    assertEquals(java.util.List.of(first.id(), second.id()), reordered.items().stream().map(item -> item.questionId()).toList());

    service.activateQuestionSet(activity.id(), set.id());
    assertEquals(java.util.List.of(first.id(), second.id()), service.listQuestionControl(activity.id()).stream()
        .map(item -> item.id()).toList());
    assertEquals(set.id(), service.activity(activity.id()).activeQuestionSetId());
  }
}
