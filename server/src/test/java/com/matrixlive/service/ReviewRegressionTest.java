package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "APP_BOOTSTRAP_PASSWORD=ChangeMe!2026",
      "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE="
    })
@Transactional
class ReviewRegressionTest {
  @Autowired ActivityService service;
  @Autowired com.matrixlive.repository.ActivityRepository activities;
  @Autowired com.matrixlive.repository.QuestionRepository questions;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private ActivityResponse activity() {
    var activity =
        service.createActivity(
            new CreateActivityRequest("Review regression", "Shanghai", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("a", "A", 10, true));
    return activity;
  }

  private ParticipantResponse person(UUID activityId) {
    return com.matrixlive.service.QuizTestSupport.register(service,
        activityId,
        "a",
        new RegisterParticipantRequest("Player", UUID.randomUUID().toString(), null));
  }

  private QuestionWriteRequest text(String title) {
    return new QuestionWriteRequest(
        "TEXT", title, List.of(), Set.of(), 100, 0, null, 40, List.of("東京"), "FUZZY", true);
  }

  @Test
  void pendingReviewNeverPrecreditsTheIncorrectPenaltyAndRegradingIsIdempotent() {
    var a = activity();
    activities.findById(a.id()).orElseThrow().updateScoring("SIMPLE", 100, 50, "{}");
    var p = person(a.id());
    var q = service.createQuestion(a.id(), text("City?"));
    com.matrixlive.service.QuizTestSupport.start(service, a.id());
    service.control(a.id(), new ControlRequest("QUESTION_OPEN", q.id(), 30));
    var answer =
        service.submitAnswer(
            a.id(),
            new SubmitAnswerRequest(p.id(), q.id(), Set.of("東"), UUID.randomUUID().toString()));
    assertEquals("PENDING_REVIEW", answer.status());
    assertEquals(0, answer.awardedPoints());
    assertEquals(0, answer.totalScore());
    var wrong =
        service.gradeSubmission(a.id(), answer.submissionId(), new GradeSubmissionRequest(0, null));
    assertEquals(-50, wrong.awardedPoints());
    assertEquals(-50, service.participant(a.id(), p.id()).score());
    service.gradeSubmission(a.id(), answer.submissionId(), new GradeSubmissionRequest(0, null));
    assertEquals(-50, service.participant(a.id(), p.id()).score());
    service.gradeSubmission(a.id(), answer.submissionId(), new GradeSubmissionRequest(100, null));
    assertEquals(100, service.participant(a.id(), p.id()).score());
    var stats = service.questionResponseStats(a.id(), q.id());
    assertEquals(1, stats.correctCount());
    assertEquals(1, stats.submittedCount());
    assertEquals(answer.responseRank(), stats.submissions().getFirst().responseRank());
    assertThrows(
        DomainException.class,
        () ->
            service.gradeSubmission(
                a.id(), answer.submissionId(), new GradeSubmissionRequest(-1, null)));
  }

  @Test
  void fuzzyMatchingRequiresTheWholeExpectedAnswer() {
    assertFalse(AnswerScorer.matchesText("東", List.of("東京"), "FUZZY"));
    assertFalse(AnswerScorer.matchesText("a", List.of("the answer"), "FUZZY"));
    assertTrue(AnswerScorer.matchesText("the answer is 東京", List.of("東京"), "FUZZY"));
  }

  @Test
  void savesLongQuestionContentBeyondTheOldVarcharLimit() {
    var a = activity();
    String longOption = "x".repeat(500);
    var q =
        service.createQuestion(
            a.id(),
            new QuestionWriteRequest(
                "SINGLE",
                "题".repeat(8000),
                List.of(longOption, "Other"),
                Set.of(longOption),
                100,
                0,
                null,
                40,
                true));
    questions.flush();
    assertEquals(8000, q.title().length());
    assertEquals(longOption, q.answers().getFirst());
  }

  @Test
  void pausedActivityRejectsAnswersAndRequiresAnExplicitReopen() {
    var a = activity();
    var p = person(a.id());
    var q = service.createQuestion(a.id(), text("Question"));
    service.changeActivityStatus(a.id(), new ChangeActivityStatusRequest("LIVE"));
    com.matrixlive.service.QuizTestSupport.start(service, a.id());
    service.control(a.id(), new ControlRequest("QUESTION_OPEN", q.id(), 30));
    service.changeActivityStatus(a.id(), new ChangeActivityStatusRequest("PAUSED"));
    assertThrows(
        DomainException.class,
        () ->
            service.submitAnswer(
                a.id(), new SubmitAnswerRequest(p.id(), q.id(), Set.of("東京"), "paused")));
    assertEquals("LOBBY", service.controlState(a.id()).stage());
    service.changeActivityStatus(a.id(), new ChangeActivityStatusRequest("LIVE"));
    assertThrows(
        DomainException.class,
        () ->
            service.submitAnswer(
                a.id(), new SubmitAnswerRequest(p.id(), q.id(), Set.of("東京"), "resumed")));
  }

  @Test
  void adjustmentReplayDoesNotCreditTwiceAndRejectsAChangedOperation() {
    var a = activity();
    var p = person(a.id());
    var request = new ManualScoreRequest(p.id(), 20, "Manual", "one-operation");
    var first = service.adjustScore(a.id(), request);
    assertEquals(first.id(), service.adjustScore(a.id(), request).id());
    assertEquals(20, service.participant(a.id(), p.id()).score());
    assertThrows(
        DomainException.class,
        () ->
            service.adjustScore(
                a.id(), new ManualScoreRequest(p.id(), 30, "Manual", "one-operation")));
  }

  @Test
  void preservesOptionsThatStartWithABracketAndAggregatesSubmissionsInOneCall() {
    var a = activity();
    var p = person(a.id());
    var q =
        service.createQuestion(
            a.id(),
            new QuestionWriteRequest(
                "SINGLE",
                "Bracket",
                List.of("[A] text", "B"),
                Set.of("[A] text"),
                100,
                0,
                null,
                40,
                true));
    com.matrixlive.service.QuizTestSupport.start(service, a.id());
    service.control(a.id(), new ControlRequest("QUESTION_OPEN", q.id(), 30));
    service.submitAnswer(
        a.id(), new SubmitAnswerRequest(p.id(), q.id(), Set.of("[A] text"), "bracket"));
    assertEquals(List.of("[A] text"), service.activitySubmissions(a.id()).getFirst().answers());
  }

  @Test
  void contactIsUniqueAcrossVenuesAndCapacityCannotDropBelowRegistrations() {
    var a = activity();
    var venue = service.listVenues(a.id()).getFirst();
    var p = person(a.id());
    service.createVenue(a.id(), new VenueRequest("b", "B", 10, true));
    assertThrows(
        DomainException.class,
        () ->
            com.matrixlive.service.QuizTestSupport.register(service,
                a.id(), "b", new RegisterParticipantRequest("Duplicate", p.contact(), null)));
    person(a.id());
    assertThrows(
        DomainException.class,
        () -> service.updateVenue(a.id(), venue.id(), new UpdateVenueRequest(null, 1, null)));
  }

  @Test
  void checkboxFieldsAcceptMultipleOptionsAndRejectForgedValues() {
    var a = activity();
    service.createRegistrationField(
        a.id(),
        new RegistrationFieldRequest("hobbies", "Hobbies", "CHECKBOX", List.of("A", "B"), true, 0));
    var p =
        com.matrixlive.service.QuizTestSupport.register(service,
            a.id(),
            "a",
            new RegisterParticipantRequest(
                "Player", "checkbox-valid", null, Map.of("hobbies", "[\"A\",\"B\"]")));
    assertEquals("[\"A\",\"B\"]", p.customFields().get("hobbies"));
    assertThrows(
        DomainException.class,
        () ->
            com.matrixlive.service.QuizTestSupport.register(service,
                a.id(),
                "a",
                new RegisterParticipantRequest(
                    "Player", "checkbox-invalid", null, Map.of("hobbies", "[\"Unknown\"]"))));
  }

  @Test
  void corruptStoredRulesFailVisibly() {
    var a = activity();
    activities.findById(a.id()).orElseThrow().updateScoring("GENERAL", 100, 0, "broken json");
    assertThrows(DomainException.class, () -> service.activity(a.id()));
  }
}
