package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.security.JwtTokenService;
import com.matrixlive.security.auth.ActivityMembership;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"APP_BOOTSTRAP_PASSWORD=ChangeMe!2026", "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE="})
@AutoConfigureMockMvc
@Transactional
class ActivityHierarchyIntegrationTest {
  @Autowired private ActivityService service;
  @Autowired private com.matrixlive.security.auth.HumanChallengeRepository challenges;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserAccountRepository users;
  @Autowired private ActivityMembershipRepository memberships;
  @Autowired private com.matrixlive.repository.ActivityRepository activities;
  @Autowired private com.matrixlive.screen.ScreenService screens;

  private ActivityResponse quiz(UUID parentId) {
    return service.createActivity(new CreateActivityRequest("Quiz round", "Shanghai", Instant.now(), null, null,
        null, null, null, null, parentId, "QUIZ"));
  }

  @Test
  void sharedQuizScoresFlowIntoMainLotteryEligibilityAndMainScreens() throws Exception {
    var parent = mainActivity();
    var quiz = quiz(parent.id());
    var lottery = lottery(parent.id());
    assertEquals(parent.id(), quiz.participantActivityId());
    service.createVenue(parent.id(), new VenueRequest("hall", "Hall", 20, true));
    assertThrows(DomainException.class, () -> service.register(parent.id(), "hall", new RegisterParticipantRequest("Draft player", "draft-should-not-register", null)));
    var person = com.matrixlive.service.QuizTestSupport.register(service, quiz.id(), "hall", new RegisterParticipantRequest("Shared player", "shared-quiz-flow", null));
    assertEquals(person.id(), service.listParticipants(parent.id(), null).getFirst().id());
    var mainScreen = screens.registerDevice(parent.id(), new com.matrixlive.screen.ScreenModels.RegisterScreenDeviceRequest("Main display", 1920, 1080)).device();
    var question = service.createQuestion(quiz.id(), new QuestionWriteRequest("SINGLE", "Pick A", List.of("A", "B"), java.util.Set.of("A"), 100, 0, null, 40, true));
    assertThrows(DomainException.class, () -> service.control(quiz.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30)));
    QuizTestSupport.start(service, quiz.id());
    service.control(quiz.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));
    assertEquals("QUESTION", screens.currentDisplay(parent.id(), mainScreen.id()).mode().name());
    String token = jwt.issueParticipantToken(parent.id(), person.id()).value();
    mvc.perform(post("/api/activities/" + quiz.id() + "/answers").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new SubmitAnswerRequest(person.id(), question.id(), java.util.Set.of("A"), "shared-score"))))
        .andExpect(status().isOk());
    SecurityContextHolder.clearContext();
    assertEquals(100, service.scoreboard(parent.id()).getFirst().score());
    assertEquals(100, service.scoreboard(quiz.id()).getFirst().score());
    assertEquals(100, service.scoreboard(lottery.id()).getFirst().score());
    var second = quiz(parent.id());
    QuizTestSupport.start(service, second.id());
    service.adjustScore(second.id(), new ManualScoreRequest(person.id(), 50, "Second round"));
    assertEquals(50, service.scoreboard(second.id()).getFirst().score());
    assertEquals(100, service.scoreboard(quiz.id()).getFirst().score());
    assertEquals(150, service.scoreboard(parent.id()).getFirst().score());
    var pool = service.createPrizePool(lottery.id(), new PrizePoolRequest("qualified", "Qualified prize", "LOTTERY", "PHYSICAL", null, null, 2, 120, 1, null, null, true));
    QuizTestSupport.start(service, lottery.id());
    var drawRequest = new HostDrawRequest(pool.id(), "host-qualified");
    var winner = service.hostDraw(lottery.id(), drawRequest);
    assertEquals(person.id(), winner.participantId());
    assertEquals(winner.id(), service.hostDraw(lottery.id(), drawRequest).id());
    assertEquals(1, service.listPrizePools(lottery.id()).getFirst().remainingQuantity());
    assertEquals("WINNERS", screens.currentDisplay(parent.id(), mainScreen.id()).mode().name());
    assertThrows(DomainException.class, () -> service.hostDraw(lottery.id(), new HostDrawRequest(pool.id(), "no-repeat")));
    mvc.perform(post("/api/activities/" + lottery.id() + "/host-draws").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(drawRequest))).andExpect(status().isForbidden());
  }

  @Test
  void buzzGradingIsIdempotentAndPauseStopsEveryChildUntilExplicitResume() {
    var parent = mainActivity();
    var quiz = quiz(parent.id());
    service.createVenue(parent.id(), new VenueRequest("hall", "Hall", 20, true));
    var person = com.matrixlive.service.QuizTestSupport.register(service, parent.id(), "hall", new RegisterParticipantRequest("Buzzer", "shared-buzz-grade", null));
    service.updateActivity(quiz.id(), new UpdateActivityRequest(null, null, null, null, null, null, null, null, null, null, null,
        null, 100, 20, null, null, null, "BUZZER"));
    var question = service.createQuestion(quiz.id(), new QuestionWriteRequest("SINGLE", "Pick A", List.of("A", "B"), java.util.Set.of("A"), 100, 0, null, 40, true));
    QuizTestSupport.start(service, quiz.id());
    service.control(quiz.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30));
    service.buzz(quiz.id(), new BuzzRequest(person.id(), question.id()));
    service.gradeBuzz(quiz.id(), question.id(), person.id(), new GradeBuzzRequest(true, "Correct"));
    service.gradeBuzz(quiz.id(), question.id(), person.id(), new GradeBuzzRequest(true, "Correct again"));
    assertTrue(service.buzzes(quiz.id(), question.id()).getFirst().correct());
    assertEquals(100, service.scoreboard(parent.id()).getFirst().score());
    service.gradeBuzz(quiz.id(), question.id(), person.id(), new GradeBuzzRequest(false, "Correction"));
    assertFalse(service.buzzes(quiz.id(), question.id()).getFirst().correct());
    assertEquals(-20, service.scoreboard(parent.id()).getFirst().score());
    assertEquals(-20, service.scoreboard(quiz.id()).getFirst().score());
    assertEquals(2, service.scoreLedger(quiz.id(), person.id()).size());
    service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("PAUSED"));
    assertEquals("PAUSED", service.activity(quiz.id()).status());
    assertEquals("LOBBY", service.controlState(quiz.id()).stage());
    assertThrows(DomainException.class, () -> service.buzz(quiz.id(), new BuzzRequest(person.id(), question.id())));
    assertThrows(DomainException.class, () -> service.control(quiz.id(), new ControlRequest("QUESTION_OPEN", question.id(), 30)));
    service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("LIVE"));
    assertEquals("PAUSED", service.activity(quiz.id()).status());
    assertThrows(DomainException.class, () -> service.updateActivity(quiz.id(), new UpdateActivityRequest(null, null, null, null,
        null, null, null, null, null, null, "LOTTERY", null, null, null, null, null, null)));
    service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("FINISHED"));
    assertEquals("FINISHED", service.activity(quiz.id()).status());
    assertThrows(DomainException.class, () -> lottery(parent.id()));
    assertEquals("Archive note", service.updateActivity(quiz.id(), new UpdateActivityRequest(null, null, null, null,
        "Archive note", null, null, null, null, null, null, null, null, null, null, null, null)).description());
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void simultaneousHostRetriesIssueOneAwardAndNeverRepeatWinners() throws Exception {
    var parent = mainActivity();
    var lottery = lottery(parent.id());
    service.createVenue(parent.id(), new VenueRequest("hall", "Hall", 20, true));
    for (int i = 0; i < 2; i++) com.matrixlive.service.QuizTestSupport.register(service, parent.id(), "hall", new RegisterParticipantRequest("Winner " + i, "host-concurrent-" + i, null));
    var pool = service.createPrizePool(lottery.id(), new PrizePoolRequest("concurrent", "Prize", "LOTTERY", "PHYSICAL", null, null, 3, 0, 1, null, null, true));
    QuizTestSupport.start(service, lottery.id());
    var request = new HostDrawRequest(pool.id(), "same-host-attempt");
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> { start.await(); return service.hostDraw(lottery.id(), request); });
      var retry = executor.submit(() -> { start.await(); return service.hostDraw(lottery.id(), request); });
      start.countDown();
      assertEquals(first.get(10, java.util.concurrent.TimeUnit.SECONDS).id(), retry.get(10, java.util.concurrent.TimeUnit.SECONDS).id());
    }
    assertEquals(1, service.allAwards(lottery.id(), null).size());
    service.hostDraw(lottery.id(), new HostDrawRequest(pool.id(), "second-host-attempt"));
    assertEquals(2, service.allAwards(lottery.id(), null).stream().map(AwardDetailResponse::participantId).distinct().count());
    assertThrows(DomainException.class, () -> service.hostDraw(lottery.id(), new HostDrawRequest(pool.id(), "third-host-attempt")));
    assertEquals(1, service.listPrizePools(lottery.id()).getFirst().remainingQuantity());
  }

  @Test
  void lotterySharesMainRegistrationButKeepsItsChancesAndAwardsSeparate() throws Exception {
    var parent = mainActivity();
    var lottery = lottery(parent.id());
    service.createVenue(parent.id(), new VenueRequest("main", "Main Hall", 10, true));
    service.createRegistrationField(parent.id(), new RegistrationFieldRequest("team", "Team", "TEXT", List.of(), true, 0));
    var person = com.matrixlive.service.QuizTestSupport.register(service, lottery.id(), "main",
        new RegisterParticipantRequest("Taylor", "13800000001", "QA", Map.of("team", "A")));
    assertEquals(person.id(), service.listParticipants(parent.id(), null).getFirst().id());
    assertEquals(person.id(), service.listParticipants(lottery.id(), null).getFirst().id());
    assertEquals(service.listVenues(parent.id()), service.listVenues(lottery.id()));
    assertEquals(service.registrationFields(parent.id()), service.registrationFields(lottery.id()));

    var pool = service.createPrizePool(lottery.id(), new PrizePoolRequest("gift", "Gift", "LOTTERY", "DIGITAL",
        null, "https://example.test/gift", 3, 0, 1, null, null, true));
    service.grantLotteryChances(lottery.id(), person.id(), new GrantLotteryChancesRequest(2, "Registration"));
    assertEquals(0, service.lotteryChance(parent.id(), person.id()).remainingDraws());
    assertEquals(2, service.participant(lottery.id(), person.id()).lotteryChance().remainingDraws());
    assertThrows(DomainException.class, () -> service.draw(lottery.id(), new DrawRequest(person.id(), pool.id(), "draft")));
    service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("LIVE"));
    service.changeActivityStatus(lottery.id(), new ChangeActivityStatusRequest("LIVE"));

    String token = jwt.issueParticipantToken(parent.id(), person.id()).value();
    String path = "/api/activities/" + lottery.id();
    mvc.perform(get(path + "/participants/" + person.id()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(post(path + "/draws").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new DrawRequest(person.id(), pool.id(), "main", "shared-token"))))
        .andExpect(status().isOk());
    assertEquals(1, service.awards(lottery.id(), person.id()).size());
    assertTrue(service.awards(parent.id(), person.id()).isEmpty());
    assertEquals(1, service.lotteryChance(lottery.id(), person.id()).remainingDraws());

    mvc.perform(post("/api/auth/participant-token").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("activityId", lottery.id(), "venue", "main", "contact", person.contact(), "challengeId", com.matrixlive.security.auth.TestChallenges.create(challenges), "challengeAnswer", "ABCDE"))))
        .andExpect(status().isOk());
    var other = lottery(mainActivity().id());
    mvc.perform(get("/api/activities/" + other.id() + "/participants/" + person.id())
            .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    mvc.perform(post(path + "/draws").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new DrawRequest(UUID.randomUUID(), pool.id(), "impersonation"))))
        .andExpect(status().isForbidden());

    SecurityContextHolder.clearContext();
    service.terminateActivity(parent.id());
    assertEquals("CANCELLED", service.activity(lottery.id()).status());
    assertThrows(DomainException.class, () -> service.draw(lottery.id(), new DrawRequest(person.id(), pool.id(), "closed")));
    assertTrue(service.draw(lottery.id(), new DrawRequest(person.id(), pool.id(), "shared-token")).replayed());
  }

  @Test
  void parentAdministratorCanCreateAndEnableLotteryButCannotAttachItElsewhere() throws Exception {
    var parent = mainActivity();
    service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("LIVE"));
    var other = mainActivity();
    var admin = users.save(new UserAccount("hierarchy-" + UUID.randomUUID(), "Main admin", "unused", null));
    memberships.save(new ActivityMembership(parent.id(), admin.getId(), UserRole.ACTIVITY_ADMIN));
    String token = jwt.issueAccountToken(admin).value();
    var child = lottery(parent.id());
    var response = mvc.perform(post("/api/activities/" + child.id() + "/status")
            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"LIVE\"}"))
        .andExpect(status().isOk()).andReturn();
    assertEquals("ACTIVITY_ADMIN", mapper.readTree(response.getResponse().getContentAsString()).get("viewerRole").asText());
    var request = new CreateActivityRequest("Second lottery", "Shanghai", Instant.now(), null, null,
        null, null, null, null, parent.id(), "LOTTERY");
    mvc.perform(post("/api/activities").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
    var unauthorized = new CreateActivityRequest("Unauthorized lottery", "Shanghai", Instant.now(), null, null,
        null, null, null, null, other.id(), "LOTTERY");
    mvc.perform(post("/api/activities").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(unauthorized)))
        .andExpect(status().isForbidden());
  }

  private ActivityResponse mainActivity() {
    SecurityContextHolder.clearContext();
    return service.createActivity(new CreateActivityRequest("Main event", "Shanghai", Instant.now()));
  }

  private ActivityResponse lottery(UUID parentId) {
    return service.createActivity(new CreateActivityRequest("Lottery", "Shanghai", Instant.now(), null, null,
        null, null, null, null, parentId, "LOTTERY"));
  }
}
