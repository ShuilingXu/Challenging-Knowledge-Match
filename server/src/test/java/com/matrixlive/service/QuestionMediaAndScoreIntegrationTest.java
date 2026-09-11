package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.matrixlive.domain.Question;
import com.matrixlive.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class QuestionMediaAndScoreIntegrationTest {
  @Autowired private ActivityService service;
  @Autowired private QuestionRepository questions;
  @Autowired private EntityManager entityManager;

  @Test
  void persistsOrderedMediaAndExposesItToEveryQuestionView() {
    var activity = service.createActivity(new CreateActivityRequest("Question media", "Shanghai", Instant.now()));
    var urls = List.of("https://example.test/first.png?caption=%22quoted%22", "https://example.test/second.png");
    var question = service.createQuestion(activity.id(), request(25, urls));
    entityManager.flush();
    entityManager.clear();

    var stored = questions.findById(question.id()).orElseThrow();
    assertEquals(urls, stored.getMediaUrls());
    assertEquals(urls.getFirst(), stored.getMediaUrl());
    assertEquals(urls, service.listQuestions(activity.id()).getFirst().mediaUrls());
    assertEquals(urls, service.listQuestionAdministration(activity.id()).getFirst().mediaUrls());
    assertEquals(urls, service.listQuestionControl(activity.id()).getFirst().mediaUrls());
    assertEquals(25, service.listQuestions(activity.id()).getFirst().fullScore());

    var edited = service.updateQuestion(activity.id(), question.id(), request(35, null));
    assertEquals(urls, edited.mediaUrls());
    assertEquals(35, edited.fullScore());
    var cleared = service.updateQuestion(activity.id(), question.id(), request(35, List.of()));
    entityManager.flush();
    entityManager.clear();
    assertEquals(List.of(), cleared.mediaUrls());
    assertEquals("", cleared.mediaUrl());
    assertEquals(List.of(), questions.findById(question.id()).orElseThrow().getMediaUrls());
  }

  @Test
  void readsLegacyMediaAndAllowsLegacyClientsToReplaceOrRemoveIt() {
    var activity = service.createActivity(new CreateActivityRequest("Legacy question", "Shanghai", Instant.now()));
    var legacy = questions.save(new Question(activity.id(), "SINGLE", "Pick A", "A|B", "A", 15,
        0, "https://example.test/legacy.png", 40, "[]", "MANUAL"));
    entityManager.flush();
    entityManager.clear();
    assertEquals(List.of("https://example.test/legacy.png"), service.listQuestions(activity.id()).getFirst().mediaUrls());

    var edited = service.updateQuestion(activity.id(), legacy.getId(), new QuestionWriteRequest("SINGLE", "Pick A",
        List.of("A", "B"), Set.of("A"), 15, 0, "https://example.test/new.png", 40, true));
    assertEquals(List.of("https://example.test/new.png"), edited.mediaUrls());
    var cleared = service.updateQuestion(activity.id(), legacy.getId(), new QuestionWriteRequest("SINGLE", "Pick A",
        List.of("A", "B"), Set.of("A"), 15, 0, "", 40, true));
    assertEquals(List.of(), cleared.mediaUrls());
    assertEquals("", cleared.mediaUrl());
  }

  @Test
  void awardsTheConfiguredScoreAfterEditing() {
    var activity = service.createActivity(new CreateActivityRequest("Question score", "Shanghai", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall", "Hall", 10, true));
    var participant = service.register(activity.id(), "hall", new RegisterParticipantRequest("Test", "13900006789", ""));
    var question = service.createQuestion(activity.id(), request(25, List.of()));
    service.updateQuestion(activity.id(), question.id(), request(37, null));
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 60));
    var result = service.submitAnswer(activity.id(), new SubmitAnswerRequest(participant.id(), question.id(),
        Set.of("A"), UUID.randomUUID().toString()));
    assertEquals(37, result.awardedPoints());
    assertEquals(37, result.totalScore());
  }

  @Test
  void rejectsOutOfRangeScoresAndMediaLists() {
    var activity = service.createActivity(new CreateActivityRequest("Question validation", "Shanghai", Instant.now()));
    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), request(0, List.of())));
    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), request(100001, List.of())));
    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), request(10,
        IntStream.range(0, 21).mapToObj(index -> "https://example.test/" + index + ".png").toList())));
    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), request(10, List.of(" "))));
    assertThrows(DomainException.class, () -> service.createQuestion(activity.id(), request(10, List.of("x".repeat(2049)))));
  }

  private QuestionWriteRequest request(int fullScore, List<String> mediaUrls) {
    return new QuestionWriteRequest("SINGLE", "Pick A", List.of("A", "B"), Set.of("A"), fullScore,
        0, null, 40, List.of(), "MANUAL", true, mediaUrls);
  }
}
