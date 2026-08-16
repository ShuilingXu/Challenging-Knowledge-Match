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
  }

  @Test
  void recordsScoreLedgerAndMakesLotteryDrawsPersistentAndIdempotent() {
    var activity = service.createActivity(new CreateActivityRequest("Scoring workflow", "Guangzhou", Instant.now()));
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
  }

  @Test
  void holdsTextAnswersForManualReviewAndAppliesFeedbackWhenGraded() {
    var activity = service.createActivity(new CreateActivityRequest("Text review", "Beijing", Instant.now()));
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
}
