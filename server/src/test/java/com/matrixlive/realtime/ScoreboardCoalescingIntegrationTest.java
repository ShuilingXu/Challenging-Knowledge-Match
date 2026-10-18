package com.matrixlive.realtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.matrixlive.api.ApiModels.ControlRequest;
import com.matrixlive.api.ApiModels.CreateActivityRequest;
import com.matrixlive.api.ApiModels.QuestionWriteRequest;
import com.matrixlive.api.ApiModels.RegisterParticipantRequest;
import com.matrixlive.api.ApiModels.SubmitAnswerRequest;
import com.matrixlive.api.ApiModels.VenueRequest;
import com.matrixlive.service.ActivityService;
import com.matrixlive.service.QuizTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Guards the broadcast-storm fix: a burst of answers must collapse into a handful of "the scores
 * moved" notifications instead of one notification per answer, because every subscribed client
 * reloads on each notification.
 *
 * <p>Deliberately not {@code @Transactional}: the coalescing flush runs on its own thread in a new
 * transaction, so it can only observe committed rows.
 */
@SpringBootTest(properties = {
    "APP_BOOTSTRAP_PASSWORD=ChangeMe!2026",
    "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE=",
    // Real windows so the coalescing path is exercised; the suite default sets these to 0.
    "app.realtime.score-broadcast-window-ms=200",
    "app.screens.refresh-window-ms=200",
    "app.cache.activity-ttl-seconds=0"
})
class ScoreboardCoalescingIntegrationTest {
  private static final int ANSWERS = 20;

  @Autowired private ActivityService service;
  @MockitoSpyBean private SimpMessagingTemplate messaging;

  @Test
  void burstOfAnswersCollapsesIntoFewScoreNotifications() {
    var activity = QuizTestSupport.create(service,
        new CreateActivityRequest("Coalescing burst", "Shanghai", Instant.now()));
    service.createVenue(activity.id(), new VenueRequest("hall", "Hall", 30, true));
    List<UUID> people = new ArrayList<>();
    for (int index = 0; index < ANSWERS; index++) {
      people.add(QuizTestSupport.register(service, activity.id(), "hall",
          new RegisterParticipantRequest("Person " + index, "coalesce-contact-" + index, null)).id());
    }
    var question = service.createQuestion(activity.id(), new QuestionWriteRequest("SINGLE", "Pick A",
        List.of("A", "B"), Set.of("A"), 100, 0, null, 40, true));
    QuizTestSupport.start(service, activity.id());
    service.control(activity.id(), new ControlRequest("QUESTION_OPEN", question.id(), 600));

    String topic = "/topic/activities/" + activity.id();

    // Host-driven state changes must stay immediate, otherwise the question would not open for anyone.
    assertTrue(hasMessageType(topic, "control.updated"),
        "control.updated must be delivered immediately, not coalesced");

    // Drain any registration notifications still inside their window so they cannot pollute the count.
    sleep(800);
    Mockito.clearInvocations(messaging);

    for (UUID person : people) {
      service.submitAnswer(activity.id(),
          new SubmitAnswerRequest(person, question.id(), Set.of("A"), "coalesce-" + person));
    }

    // Let the trailing windows flush.
    sleep(1500);

    long notifications = messagesTo(topic);
    System.out.println("[coalescing] " + ANSWERS + " answers produced " + notifications + " score notifications");
    assertTrue(notifications >= 1,
        "the burst must still tell clients the scores moved");
    assertTrue(notifications <= 5,
        ANSWERS + " answers must not produce one notification each; observed " + notifications);
  }

  private long messagesTo(String destination) {
    return Mockito.mockingDetails(messaging).getInvocations().stream()
        .filter(invocation -> "convertAndSend".equals(invocation.getMethod().getName()))
        .filter(invocation -> invocation.getArguments().length == 2)
        .filter(invocation -> destination.equals(invocation.getArgument(0, Object.class)))
        .count();
  }

  private boolean hasMessageType(String destination, String type) {
    return Mockito.mockingDetails(messaging).getInvocations().stream()
        .filter(invocation -> "convertAndSend".equals(invocation.getMethod().getName()))
        .filter(invocation -> invocation.getArguments().length == 2)
        .filter(invocation -> destination.equals(invocation.getArgument(0, Object.class)))
        .map(invocation -> invocation.getArgument(1, Object.class))
        .filter(payload -> payload instanceof Map<?, ?>)
        .anyMatch(payload -> type.equals(((Map<?, ?>) payload).get("type")));
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
