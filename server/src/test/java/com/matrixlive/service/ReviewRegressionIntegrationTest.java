package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;

import com.matrixlive.domain.AnswerSubmission;
import com.matrixlive.repository.AnswerSubmissionRepository;
import com.matrixlive.screen.ScreenModels.RegisterScreenDeviceRequest;
import com.matrixlive.screen.ScreenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReviewRegressionIntegrationTest {
  @Autowired ActivityService service;
  @Autowired ScreenService screens;
  @Autowired AnswerSubmissionRepository submissions;

  private ActivityResponse activity() {
    var activity = service.createActivity(new CreateActivityRequest("Review regression", "Shanghai", Instant.now()));
    service.updateActivity(activity.id(), new UpdateActivityRequest(null, null, null, null, null, null, null,
        null, null, null, null, "SIMPLE", 100, 20, null, null, null));
    service.createVenue(activity.id(), new VenueRequest("hall", "Hall", 20, true));
    return activity;
  }

  private QuestionAdminResponse manual(UUID activityId) {
    return service.createQuestion(activityId, new QuestionWriteRequest("TEXT", "Explain", List.of(), Set.of(),
        100, 0, null, 0, List.of(), "MANUAL", true));
  }

  private AnswerResult answer(UUID activityId, UUID participantId, UUID questionId) {
    return service.submitAnswer(activityId, new SubmitAnswerRequest(participantId, questionId,
        Set.of("An explanation"), UUID.randomUUID().toString()));
  }

  @Test
  void pendingAnswersAreUnpostedAndRepeatedGradingBalancesTheLedgerAndStatistics() {
    var activity = activity();
    var p = service.register(activity.id(), "hall", new RegisterParticipantRequest("Player", "manual-first", null));
    var q = manual(activity.id());
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", q.id(), 600));
    var s = answer(activity.id(), p.id(), q.id());
    assertEquals(0, s.awardedPoints());
    assertEquals(0, s.totalScore());
    assertTrue(service.scoreLedger(activity.id(), p.id()).isEmpty());
    assertEquals(1, service.questionResponseStats(activity.id(), q.id()).pendingReviewCount());

    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(100, null));
    assertEquals(100, service.participant(activity.id(), p.id()).score());
    assertEquals(1, service.questionResponseStats(activity.id(), q.id()).correctCount());
    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(100, null));
    assertEquals(100, service.participant(activity.id(), p.id()).score());
    assertEquals(1, service.scoreLedger(activity.id(), p.id()).size());
    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(0, null));
    assertEquals(-20, service.participant(activity.id(), p.id()).score());
    assertEquals(1, service.questionResponseStats(activity.id(), q.id()).incorrectCount());
    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(50, null));
    assertEquals(50, service.participant(activity.id(), p.id()).score());
    assertEquals(50, service.scoreLedger(activity.id(), p.id()).stream().mapToInt(ScoreLedgerResponse::points).sum());
    assertEquals(1, service.questionResponseStats(activity.id(), q.id()).partialCount());
  }

  @Test
  void firstIncorrectGradeAndLegacyPendingPenaltyDoNotUseUnpostedAmounts() {
    var activity = activity();
    var q = manual(activity.id());
    var p = service.register(activity.id(), "hall", new RegisterParticipantRequest("Incorrect", "manual-zero", null));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", q.id(), 600));
    var s = answer(activity.id(), p.id(), q.id());
    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(0, null));
    assertEquals(-20, service.participant(activity.id(), p.id()).score());

    var legacy = service.register(activity.id(), "hall", new RegisterParticipantRequest("Legacy", "manual-legacy", null));
    var old = submissions.save(new AnswerSubmission(activity.id(), legacy.id(), q.id(), UUID.randomUUID().toString(),
        "legacy", -20, "PENDING_REVIEW", null));
    service.gradeSubmission(activity.id(), old.getId(), new GradeSubmissionRequest(100, null));
    assertEquals(100, service.participant(activity.id(), legacy.id()).score());
    var stats = service.questionResponseStats(activity.id(), q.id());
    assertEquals(stats.submittedCount(), stats.correctCount() + stats.partialCount() + stats.incorrectCount() + stats.pendingReviewCount());
  }

  @Test
  void pauseRejectsAnswersWithoutCreatingSubmissionsOrScores() {
    var activity = activity();
    var p = service.register(activity.id(), "hall", new RegisterParticipantRequest("Paused", "pause", null));
    var q = manual(activity.id());
    service.changeActivityStatus(activity.id(), new ChangeActivityStatusRequest("LIVE"));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", q.id(), 600));
    service.changeActivityStatus(activity.id(), new ChangeActivityStatusRequest("PAUSED"));
    assertEquals(HttpStatus.CONFLICT, assertThrows(DomainException.class,
        () -> answer(activity.id(), p.id(), q.id())).getStatus());
    assertTrue(service.submissions(activity.id(), p.id()).isEmpty());
    assertEquals(0, service.participant(activity.id(), p.id()).score());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void scoreAndGradeUpdatesRefreshPublishedDeviceSnapshots() {
    var activity = activity();
    var p = service.register(activity.id(), "hall", new RegisterParticipantRequest("Live", "screen-live", null));
    var q = manual(activity.id());
    var device = screens.registerDevice(activity.id(), new RegisterScreenDeviceRequest("Live screen", 1920, 1080)).device();
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", q.id(), 600));
    var s = answer(activity.id(), p.id(), q.id());
    service.control(activity.id(), new ControlRequest("ANSWER_REVEALED", q.id(), 0));
    service.gradeSubmission(activity.id(), s.submissionId(), new GradeSubmissionRequest(100, null));
    var responses = (List<?>) screens.currentDisplay(activity.id(), device.id()).data().get("responses");
    assertEquals("CORRECT", ((Map<?, ?>) responses.getFirst()).get("status"));
    assertEquals(100, ((Number) ((Map<?, ?>) responses.getFirst()).get("awardedPoints")).intValue());
    service.control(activity.id(), new ControlRequest("SCOREBOARD", null, 0));
    service.adjustScore(activity.id(), new ManualScoreRequest(p.id(), -10, null));
    var board = (List<?>) screens.currentDisplay(activity.id(), device.id()).data().get("rows");
    assertEquals(90, ((Number) ((Map<?, ?>) board.getFirst()).get("score")).intValue());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRegistrationsRespectCapacityInIndependentTransactions() throws Exception {
    var activity = activity();
    service.createVenue(activity.id(), new VenueRequest("tiny", "Tiny", 1, true));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(12)) {
      var futures = java.util.stream.IntStream.range(0, 12).mapToObj(i -> executor.submit(() -> {
        start.await();
        try {
          service.register(activity.id(), "tiny", new RegisterParticipantRequest("Concurrent " + i, "capacity-" + i, null));
          return true;
        } catch (DomainException e) {
          assertEquals(HttpStatus.CONFLICT, e.getStatus());
          return false;
        }
      })).toList();
      start.countDown();
      int success = 0;
      for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) success++;
      assertEquals(1, success);
      assertEquals(1, service.listParticipants(activity.id(), "tiny").size());
    }
  }
}
