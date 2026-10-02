package com.matrixlive.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.matrixlive.api.ApiModels.*;
import com.matrixlive.security.auth.*;
import com.matrixlive.service.ActivityService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "APP_BOOTSTRAP_PASSWORD=ChangeMe!2026",
      "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE="
    })
@AutoConfigureMockMvc
class ReviewSecurityRegressionTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired HumanChallengeRepository challenges;
  @Autowired ActivityService service;
  @Autowired com.matrixlive.repository.ActivityRepository activities;
  @Autowired com.matrixlive.screen.ScreenService screens;
  @Autowired JwtTokenService jwt;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private MvcResult login() throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sysadmin\",\"password\":\"ChangeMe!2026\"}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String access(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private String participantRequest(UUID activityId, UUID challenge, String contact)
      throws Exception {
    return mapper.writeValueAsString(
        Map.of(
            "activityId",
            activityId,
            "venue",
            "south",
            "contact",
            contact,
            "challengeId",
            challenge,
            "challengeAnswer",
            "ABCDE"));
  }

  @Test
  void missingPrincipalAndMalformedSignedClaimsReturn401() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    String malformed =
        io.jsonwebtoken.Jwts.builder()
            .issuer("matrixlive-api")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    Base64.getDecoder()
                        .decode("VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE=")))
            .compact();
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + malformed))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void humanChallengeIsRequiredOneUseAndOffersNoRegistrationStatusOracle() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    mvc.perform(
            post("/api/auth/participant-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "activityId", activityId, "venue", "south", "contact", "13800002048"))))
        .andExpect(status().isBadRequest());
    UUID challenge = TestChallenges.create(challenges);
    mvc.perform(
            post("/api/auth/participant-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(participantRequest(activityId, challenge, "13800002048")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/participant-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(participantRequest(activityId, challenge, "13800002048")))
        .andExpect(status().isUnauthorized());
    UUID unknown = TestChallenges.create(challenges);
    var failed =
        mvc.perform(
                post("/api/auth/participant-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        participantRequest(activityId, unknown, "unknown-" + UUID.randomUUID())))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertFalse(failed.getResponse().getContentAsString().contains("Registration"));
    mvc.perform(get("/api/auth/human-challenge"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            jsonPath("$.image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
  }

  @Test
  void expiredClientAccessCanRenewWithoutReenteringContactAndLogoutRevokesTheWholeClientSession()
      throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    var issued =
        mvc.perform(
                post("/api/auth/participant-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        participantRequest(
                            activityId, TestChallenges.create(challenges), "13800002049")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode session = mapper.readTree(issued.getResponse().getContentAsString());
    String refresh = session.get("refreshToken").asText();
    var renewed =
        mvc.perform(
                post("/api/auth/client-refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
            .andExpect(status().isOk())
            .andReturn();
    mvc.perform(
            post("/api/auth/client-logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(issued)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(renewed)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/client-refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void concurrentRefreshesConvergeOnOneRefreshCookieAndLogoutRevokesAllAccessTokens()
      throws Exception {
    var initial = login();
    Cookie cookie = initial.getResponse().getCookie("matrixlive_refresh");
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<MvcResult> work =
          () -> {
            start.await();
            return mvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
          };
      var first = pool.submit(work);
      var second = pool.submit(work);
      start.countDown();
      var a = first.get(20, TimeUnit.SECONDS);
      var b = second.get(20, TimeUnit.SECONDS);
      assertEquals(
          a.getResponse().getCookie("matrixlive_refresh").getValue(),
          b.getResponse().getCookie("matrixlive_refresh").getValue());
      mvc.perform(
              post("/api/auth/logout")
                  .header("Authorization", "Bearer " + access(a))
                  .cookie(a.getResponse().getCookie("matrixlive_refresh")))
          .andExpect(status().isNoContent());
      for (var result : List.of(initial, a, b))
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(result)))
            .andExpect(status().isUnauthorized());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void refreshReuseOutsideGraceRevokesAccessTokensAndCommitsRevocation() throws Exception {
    var initial = login();
    Cookie old = initial.getResponse().getCookie("matrixlive_refresh");
    var rotated =
        mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isOk()).andReturn();
    jdbc.update(
        "update refresh_tokens set revoked_at=? where token_hash=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(20)),
        hash(old.getValue()));
    mvc.perform(post("/api/auth/refresh").cookie(old)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(rotated)))
        .andExpect(status().isUnauthorized());
  }

  private String hash(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  @Test
  void screenCredentialsRenewAndDeletedDevicesCannotRenew() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    var device =
        screens.registerDevice(
            activityId,
            new com.matrixlive.screen.ScreenModels.RegisterScreenDeviceRequest(
                "Renewal", 1920, 1080, null));
    var session =
        screens.exchangePairingToken(activityId, device.device().id(), device.pairingToken());
    mvc.perform(
            post("/api/auth/client-refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
        .andExpect(status().isOk());
    screens.deleteDevice(activityId, device.device().id());
    mvc.perform(
            post("/api/auth/client-refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void publicActivityListingAndDirectReadsExcludeDrafts() throws Exception {
    var draft =
        service.createActivity(new CreateActivityRequest("Private draft", "City", Instant.now()));
    var response = mvc.perform(get("/api/activities")).andExpect(status().isOk()).andReturn();
    assertFalse(response.getResponse().getContentAsString().contains(draft.id().toString()));
    mvc.perform(get("/api/activities/" + draft.id())).andExpect(status().isNotFound());
  }

  @Test
  void failedBatchImportRollsBackEveryPreviouslyInsertedQuestion() throws Exception {
    var activity =
        service.createActivity(new CreateActivityRequest("Atomic import", "City", Instant.now()));
    String token = access(login());
    var valid =
        new QuestionWriteRequest(
            "SINGLE", "Valid", List.of("A", "B"), Set.of("A"), 100, 0, null, 40, true);
    var invalid =
        new QuestionWriteRequest(
            "SINGLE", "Invalid", List.of("A", "B"), Set.of("C"), 100, 1, null, 40, true);
    mvc.perform(
            post("/api/activities/" + activity.id() + "/questions/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(List.of(valid, invalid))))
        .andExpect(status().isBadRequest());
    assertTrue(service.listQuestionAdministration(activity.id()).isEmpty());
  }

  @Test
  void inheritedLotteryTokenCannotReadSiblingQuizData() throws Exception {
    var parent =
        service.createActivity(new CreateActivityRequest("Root scope", "City", Instant.now()));
    service.createVenue(parent.id(), new VenueRequest("a", "A", 20, true));
    var person =
        service.register(
            parent.id(),
            "a",
            new RegisterParticipantRequest("Player", "scoped-" + UUID.randomUUID(), null));
    var lottery =
        service.createActivity(
            new CreateActivityRequest(
                "Lottery",
                "City",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                parent.id(),
                "LOTTERY"));
    var quiz =
        service.createActivity(
            new CreateActivityRequest(
                "Other quiz",
                "City",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                parent.id(),
                "QUIZ"));
    String token = jwt.issueParticipantToken(parent.id(), person.id()).value();
    mvc.perform(
            get("/api/activities/" + quiz.id() + "/questions")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/activities/" + lottery.id() + "/scoreboard")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }
}
