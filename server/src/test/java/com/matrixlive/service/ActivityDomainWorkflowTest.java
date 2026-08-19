package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActivityDomainWorkflowTest {
  @Autowired private ActivityService service;

  @Test
  void isolatesVenuesAndPersistsConfiguredRegistrationFields() {
    var activity = service.createActivity(new CreateActivityRequest("Venue workflow", "Shanghai", Instant.now()));
    service.createRegistrationField(activity.id(), new RegistrationFieldRequest("department", "Department", "SELECT",
        List.of("Engineering", "Operations"), true, 0));
    service.createVenue(activity.id(), new VenueRequest("north", "North Hall", 5, true));
    service.createVenue(activity.id(), new VenueRequest("south", "South Hall", 5, true));

    var north = service.register(activity.id(), "north",
        new RegisterParticipantRequest("Alex", "138 0000 1000", "Matrix", Map.of("department", "Engineering")));
    var south = service.register(activity.id(), "south",
        new RegisterParticipantRequest("Alex", "13800001000", "Matrix", Map.of("department", "Operations")));

    assertEquals("Engineering", north.customFields().get("department"));
    assertEquals("Operations", south.customFields().get("department"));
    assertEquals(1, service.listParticipants(activity.id(), "north").size());
    assertEquals(1, service.listParticipants(activity.id(), "south").size());
    assertThrows(DomainException.class, () -> service.register(activity.id(), "north",
        new RegisterParticipantRequest("Duplicate", "13800001000", "Matrix", Map.of("department", "Engineering"))));
    assertThrows(DomainException.class, () -> service.register(activity.id(), "west",
        new RegisterParticipantRequest("Unknown venue", "13800009999", "Matrix", Map.of("department", "Engineering"))));
  }

  @Test
  void recordsScoreLedgerAndMakesLotteryDrawsPersistentAndIdempotent() {
    var activity = service.createActivity(new CreateActivityRequest("Scoring workflow", "Guangzhou", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall-a", "Hall A", 20, true));
    var participant = service.register(activity.id(), "hall-a",
        new RegisterParticipantRequest("Taylor", "13900002000", "QA"));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("MULTIPLE", "Select all valid values",
        List.of("A", "B", "C"), Set.of("A", "B"), 100, 0, null, 40, true));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));

    var answer = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.id(), Set.of("A"),
        UUID.randomUUID().toString()));
    assertEquals(40, answer.awardedPoints());
    assertEquals(40, answer.totalScore());
    assertEquals(1, service.scoreLedger(activity.id(), participant.id()).size());

    var graded = service.gradeSubmission(activity.id(), answer.submissionId(), new GradeSubmissionRequest(70, "Manual review"));
    assertEquals(70, graded.awardedPoints());
    assertEquals(70, service.participant(activity.id(), participant.id()).score());
    assertEquals(2, service.scoreLedger(activity.id(), participant.id()).size());

    var pool = service.createPrizePool(activity.id(), new PrizePoolRequest("draw-badge", "Digital badge", "LOTTERY",
        "DIGITAL", "", "https://rewards.example.test/redeem", 1, 0, 1, null, null, true));
    service.grantLotteryChances(activity.id(), participant.id(), new GrantLotteryChancesRequest(1, "quiz completion"));
    String key = UUID.randomUUID().toString();
    var first = service.draw(activity.id(), new DrawRequest(participant.id(), pool.id(), key));
    var replay = service.draw(activity.id(), new DrawRequest(participant.id(), pool.id(), key));

    assertFalse(first.replayed());
    assertTrue(replay.replayed());
    assertEquals(first.awardId(), replay.awardId());
    assertEquals(0, service.listPrizePools(activity.id()).getFirst().remainingQuantity());
    assertEquals("REDEEMED", service.redeem(activity.id(), first.awardId()).status());
    assertThrows(DomainException.class, () -> service.draw(activity.id(), new DrawRequest(participant.id(), pool.id(),
        UUID.randomUUID().toString())));
    service.grantLotteryChances(activity.id(), participant.id(), new GrantLotteryChancesRequest(1, "venue validation"));
    assertThrows(DomainException.class, () -> service.draw(activity.id(), new DrawRequest(participant.id(), pool.id(), "other-hall",
        UUID.randomUUID().toString())));
  }

  @Test
  void rankingAwardsRequireFinishedActivityOrWinnerConfirmation() {
    var activity = service.createActivity(new CreateActivityRequest("Ranking workflow", "Shanghai", Instant.now()));
    var pool = service.createPrizePool(activity.id(), new PrizePoolRequest("rank-1", "Top prize", "RANKING",
        "DIGITAL", "", null, 1, 0, 1, 1, 1, true));
    assertThrows(DomainException.class, () -> service.issueRankingAwards(activity.id(), pool.id()));

    service.control(activity.id(), new ControlRequest("WINNERS", null, 0));
    assertTrue(service.issueRankingAwards(activity.id(), pool.id()).isEmpty());
  }

  @Test
  void holdsTextAnswersForManualReviewAndAppliesFeedbackWhenGraded() {
    var activity = service.createActivity(new CreateActivityRequest("Text review", "Beijing", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall-a", "Hall A", 20, true));
    var participant = service.register(activity.id(), "hall-a",
        new RegisterParticipantRequest("Jordan", "13900003000", "Matrix"));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("TEXT", "Describe the event highlight",
        List.of(), Set.of(), 100, 0, null, 40, true));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));

    var answer = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.id(),
        Set.of("The live scoreboard made the result clear."), UUID.randomUUID().toString()));

    assertEquals("PENDING_REVIEW", answer.status());
    assertEquals(0, answer.totalScore());
    assertTrue(service.scoreLedger(activity.id(), participant.id()).isEmpty());

    var graded = service.gradeSubmission(activity.id(), answer.submissionId(),
        new GradeSubmissionRequest(85, "观点完整，表达清晰。"));
    assertEquals("SCORED", graded.status());
    assertEquals("观点完整，表达清晰。", graded.feedback());
    assertEquals(85, service.participant(activity.id(), participant.id()).score());
  }

  @Test
  void autoScoresAcceptedTextAndStillAllowsManualAdjustment() {
    var activity = service.createActivity(new CreateActivityRequest("Automatic text scoring", "Shanghai", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall-a", "Hall A", 20, true));
    var participant = service.register(activity.id(), "hall-a",
        new RegisterParticipantRequest("Casey", "13900003001", "Matrix"));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("TEXT", "What is the answer?",
        List.of(), Set.of(), 100, 0, null, 40, List.of("the answer"), "FUZZY", true));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));

    var answer = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.id(),
        Set.of("  THE   ANSWER  "), UUID.randomUUID().toString()));

    assertEquals("CORRECT", answer.status());
    assertEquals(100, answer.awardedPoints());
    assertEquals(100, service.participant(activity.id(), participant.id()).score());
    assertEquals(List.of("the answer"), service.listQuestionAdministration(activity.id()).getFirst().textAcceptedAnswers());
    assertEquals("FUZZY", service.listQuestionAdministration(activity.id()).getFirst().textMatchMode());

    var adjusted = service.gradeSubmission(activity.id(), answer.submissionId(), new GradeSubmissionRequest(75, "人工复核"));
    assertEquals(75, adjusted.awardedPoints());
    assertEquals(75, service.participant(activity.id(), participant.id()).score());
  }

  @Test
  void unmatchedTextRemainsPendingAndRegexPatternsAreValidated() {
    var activity = service.createActivity(new CreateActivityRequest("Regex text scoring", "Shanghai", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall-a", "Hall A", 20, true));
    var participant = service.register(activity.id(), "hall-a",
        new RegisterParticipantRequest("Morgan", "13900003002", "Matrix"));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("TEXT", "Provide the code",
        List.of(), Set.of(), 100, 0, null, 40, List.of("code\\s+\\d+"), "REGEX", true));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));

    var answer = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.id(),
        Set.of("not the code"), UUID.randomUUID().toString()));
    assertEquals("PENDING_REVIEW", answer.status());
    assertEquals(0, service.participant(activity.id(), participant.id()).score());

    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), new QuestionWriteRequest("TEXT",
        "Bad regex", List.of(), Set.of(), 100, 0, null, 40, List.of("["), "REGEX", true)));
  }

  @Test
  void questionSetsControlPublishedOrderAndActivitiesSupportOneLevelSubActivities() {
    var parent = service.createActivity(new CreateActivityRequest("Shanghai knowledge event", "Shanghai", Instant.now(),
        null, "Parent event", null, null, null, null, null, "EVENT"));
    var quiz = service.createActivity(new CreateActivityRequest("Knowledge quiz", "Shanghai", Instant.now(), null,
        "Quiz child", null, null, null, null, parent.id(), "QUIZ"));
    assertEquals(parent.id(), quiz.parentActivityId());
    assertEquals("QUIZ", quiz.activityType());

    var first = service.createQuestion(quiz.id(), new QuestionWriteRequest("SINGLE", "First", List.of("A", "B"),
        Set.of("A"), 10, 0, null, 40, true));
    var second = service.createQuestion(quiz.id(), new QuestionWriteRequest("SINGLE", "Second", List.of("A", "B"),
        Set.of("B"), 10, 1, null, 40, true));
    var set = service.createQuestionSet(quiz.id(), new QuestionSetRequest("Final round", "Published order",
        List.of(second.id(), first.id()), false));
    assertEquals(List.of(second.id(), first.id()), set.items().stream().map(QuestionSetItemResponse::questionId).toList());

    service.activateQuestionSet(quiz.id(), set.id());
    assertEquals(List.of(second.id(), first.id()), service.listQuestions(quiz.id()).stream().map(QuestionResponse::id).toList());
    assertThrows(DomainException.class, () -> service.createActivity(new CreateActivityRequest("Nested", "Shanghai",
        Instant.now(), null, null, null, null, null, null, quiz.id(), "OTHER")));
    assertThrows(DomainException.class, () -> service.createActivity(new CreateActivityRequest("Invalid child", "Shanghai",
        Instant.now(), null, null, null, null, null, null, null, "QUIZ")));
  }

  @Test
  void disabledQuestionsAreExcludedWhenNoQuestionSetIsActive() {
    var activity = service.createActivity(new CreateActivityRequest("Enabled question fallback", "Shanghai", Instant.now()));
    var enabled = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Enabled", List.of("A", "B"),
        Set.of("A"), 10, 0, null, 40, true));
    service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Disabled", List.of("A", "B"),
        Set.of("B"), 10, 1, null, 40, false));

    assertEquals(List.of(enabled.id()), service.listQuestions(activity.id()).stream()
        .map(QuestionResponse::id).toList());
  }

  @Test
  void updatesExistingQuestionContentAndScoringConfiguration() {
    var activity = service.createActivity(new CreateActivityRequest("Question editing", "Shanghai", Instant.now()));
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Old title",
        List.of("A", "B"), Set.of("A"), 10, 0, null, 40, true));
    var updated = service.updateQuestion(activity.id(), question.id(), new QuestionWriteRequest("MULTIPLE", "New title",
        List.of("A", "B", "C"), Set.of("A", "C"), 20, 3, null, 50, true));
    assertEquals("New title", updated.title());
    assertEquals("MULTIPLE", updated.type());
    assertEquals(List.of("A", "C"), updated.answers());
    assertEquals(20, updated.fullScore());
    assertEquals(3, updated.displayOrder());
  }
}
