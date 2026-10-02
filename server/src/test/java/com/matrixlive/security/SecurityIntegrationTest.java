package com.matrixlive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.repository.ActivityRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"APP_BOOTSTRAP_PASSWORD=ChangeMe!2026", "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE="})
@AutoConfigureMockMvc
class SecurityIntegrationTest {
  @Autowired private com.matrixlive.security.auth.HumanChallengeRepository challenges;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ActivityRepository activities;
  @Autowired private com.matrixlive.service.ActivityService service;
  @Autowired private com.matrixlive.security.auth.AuthService auth;
  @Autowired private com.matrixlive.security.auth.RefreshTokenRepository refreshTokens;

  @Test
  void floorStaffCanAdjustAndGradeScoresButCannotReverseOrVoidAwards() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String path = "/api/activities/" + activityId;
    String staffToken = accessToken(login("event-staff", "ChangeMe!2026"));
    var person = service.register(activityId, "south", new com.matrixlive.api.ApiModels.RegisterParticipantRequest(
        "Staff score test", UUID.randomUUID() + "@test.example", null));
    var question = service.createQuestion(activityId, new com.matrixlive.api.ApiModels.QuestionWriteRequest("TEXT", "Explain",
        java.util.List.of(), java.util.Set.of(), 100, 20, null, 40, true));
    service.control(activityId, new com.matrixlive.api.ApiModels.ControlRequest("QUESTION_OPEN", question.id(), 30));
    var submission = service.submitAnswer(activityId, new com.matrixlive.api.ApiModels.SubmitAnswerRequest(person.id(),
        question.id(), java.util.Set.of("My answer"), UUID.randomUUID().toString()));
    mvc.perform(post(path + "/scores/adjustments").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"participantId\":\"" + person.id() + "\",\"points\":-10,\"idempotencyKey\":\"staff-adjust-test\"}"))
        .andExpect(status().isOk());
    mvc.perform(post(path + "/submissions/" + submission.submissionId() + "/grade").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"awardedPoints\":0,\"feedback\":\"Penalty\"}"))
        .andExpect(status().isOk());
    org.junit.jupiter.api.Assertions.assertEquals(-10, service.participant(activityId, person.id()).score());
    service.adjustScore(activityId, new com.matrixlive.api.ApiModels.ManualScoreRequest(person.id(), 10, "Restore test score"));
    var pool = service.createPrizePool(activityId, new com.matrixlive.api.ApiModels.PrizePoolRequest("batch-" + UUID.randomUUID(),
        "Batch prize", "MANUAL", "PHYSICAL", "", null, 2, 0, 1, null, null, true));
    var first = service.issueAward(activityId, new com.matrixlive.api.ApiModels.IssueAwardRequest(person.id(), pool.id(), ""));
    var second = service.issueAward(activityId, new com.matrixlive.api.ApiModels.IssueAwardRequest(person.id(), pool.id(), ""));
    mvc.perform(post(path + "/awards/redeem-batch").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"awardIds\":[\"" + first.id() + "\",\"ffffffff-ffff-ffff-ffff-ffffffffffff\"]}"))
        .andExpect(status().isNotFound());
    org.junit.jupiter.api.Assertions.assertTrue(service.awards(activityId, person.id()).stream().allMatch(award -> "PENDING".equals(award.status())));
    mvc.perform(post(path + "/awards/redeem-batch").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"awardIds\":[\"" + first.id() + "\",\"" + second.id() + "\"]}"))
        .andExpect(status().isOk());
    org.junit.jupiter.api.Assertions.assertTrue(service.awards(activityId, person.id()).stream().allMatch(award -> "REDEEMED".equals(award.status())));
    mvc.perform(post(path + "/awards/" + UUID.randomUUID() + "/reverse-redemption")
            .header("Authorization", "Bearer " + staffToken)).andExpect(status().isForbidden());
    mvc.perform(post(path + "/awards/" + UUID.randomUUID() + "/void")
            .header("Authorization", "Bearer " + staffToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/activities/" + UUID.randomUUID() + "/scores/adjustments")
            .header("Authorization", "Bearer " + staffToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"participantId\":\"" + person.id() + "\",\"points\":10}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void exposesOnlyTheHealthProbeWithoutAuthentication() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void keepsObjectStorageConfigurationOffThePublicSiteSettingsRoute() throws Exception {
    MvcResult publicSettings = mvc.perform(get("/api/site-settings"))
        .andExpect(status().isOk()).andReturn();
    JsonNode response = objectMapper.readTree(publicSettings.getResponse().getContentAsString());
    org.junit.jupiter.api.Assertions.assertNull(response.get("storageEndpoint"));
    org.junit.jupiter.api.Assertions.assertNull(response.get("storageAccessKey"));

    mvc.perform(get("/api/admin/site-settings")).andExpect(status().isUnauthorized());
    String adminToken = accessToken(login("sysadmin", "ChangeMe!2026"));
    mvc.perform(get("/api/admin/site-settings").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void rotatesRefreshTokensAndRevokesTheAccessTokenAtLogout() throws Exception {
    MvcResult login = login("sysadmin", "ChangeMe!2026");
    String access = accessToken(login);
    Cookie refresh = login.getResponse().getCookie("matrixlive_refresh");

    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());

    MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(refresh))
        .andExpect(status().isOk()).andReturn();
    String rotatedAccess = accessToken(refreshed);
    Cookie rotatedRefresh = refreshed.getResponse().getCookie("matrixlive_refresh");
    org.junit.jupiter.api.Assertions.assertNotEquals(access, rotatedAccess);

    mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + rotatedAccess).cookie(rotatedRefresh))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + rotatedAccess))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/refresh").cookie(rotatedRefresh))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void enforcesActivityRolesAndParticipantScope() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String activityPath = "/api/activities/" + activityId;

    mvc.perform(post(activityPath + "/control").contentType(MediaType.APPLICATION_JSON)
            .content("{\"stage\":\"LOBBY\",\"seconds\":30}"))
        .andExpect(status().isUnauthorized());

    MvcResult participantSession = mvc.perform(post("/api/auth/participant-token").contentType(MediaType.APPLICATION_JSON)
            .content("{\"activityId\":\"" + activityId + "\",\"venue\":\"south\",\"contact\":\"13800002048\",\"challengeId\":\"" + com.matrixlive.security.auth.TestChallenges.create(challenges) + "\",\"challengeAnswer\":\"ABCDE\"}"))
        .andExpect(status().isOk()).andReturn();
    String participantToken = accessToken(participantSession);

    mvc.perform(get(activityPath + "/questions").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/control").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/questions/control").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isForbidden());
    mvc.perform(post(activityPath + "/control").header("Authorization", "Bearer " + participantToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"LOBBY\",\"seconds\":30}"))
        .andExpect(status().isForbidden());

    String staffToken = accessToken(login("event-staff", "ChangeMe!2026"));
    mvc.perform(get(activityPath + "/questions/control").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/questions/admin").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(post(activityPath + "/control").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"LOBBY\",\"seconds\":30}"))
        .andExpect(status().isOk());
    mvc.perform(post(activityPath + "/questions").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void activityAdminCanCreateAnActivityAndReceivesItsMembership() throws Exception {
    String activityAdminToken = accessToken(login("activity-admin", "ChangeMe!2026"));
    MvcResult created = mvc.perform(post("/api/activities").header("Authorization", "Bearer " + activityAdminToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Scoped activity\",\"city\":\"Shanghai\"}"))
        .andExpect(status().isCreated()).andReturn();
    String activityId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get("/api/activities/" + activityId + "/memberships")
            .header("Authorization", "Bearer " + activityAdminToken))
        .andExpect(status().isOk());
  }

  @Test
  void activityAdminCanListAccountsForActivityAccessManagement() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String activityPath = "/api/activities/" + activityId;
    String activityAdminToken = accessToken(login("activity-admin", "ChangeMe!2026"));
    String staffToken = accessToken(login("event-staff", "ChangeMe!2026"));

    mvc.perform(get(activityPath + "/memberships/users")
            .header("Authorization", "Bearer " + activityAdminToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/memberships/users")
            .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void systemAdminCanCreateAssignAndEditAnActivityUserAccount() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String adminToken = accessToken(login("sysadmin", "ChangeMe!2026"));
    String username = "acceptance-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    MvcResult created = mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"displayName\":\"Acceptance User\","
                + "\"password\":\"TempPass!2026\",\"systemRole\":null}"))
        .andExpect(status().isCreated()).andReturn();
    String userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(post("/api/activities/" + activityId + "/memberships")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"role\":\"STAFF\"}"))
        .andExpect(status().isCreated());

    mvc.perform(patch("/api/admin/users/" + userId).header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"displayName\":\"Edited User\","
                + "\"password\":\"TempPass!2027\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void pairedScreenDeviceGetsOnlyItsScopedStateAndHeartbeatAccess() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String activityPath = "/api/activities/" + activityId;
    String adminToken = accessToken(login("activity-admin", "ChangeMe!2026"));
    MvcResult registered = mvc.perform(post(activityPath + "/screens/devices/register")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Main screen\",\"viewportWidth\":1920,\"viewportHeight\":1080}"))
        .andExpect(status().isCreated()).andReturn();
    JsonNode registration = objectMapper.readTree(registered.getResponse().getContentAsString());
    String deviceId = registration.at("/device/id").asText();
    String pairingToken = registration.get("pairingToken").asText();

    MvcResult session = mvc.perform(post(activityPath + "/screens/devices/" + deviceId + "/session")
            .header("X-Screen-Pairing", pairingToken))
        .andExpect(status().isOk()).andReturn();
    String deviceToken = accessToken(session);

    mvc.perform(get(activityPath + "/screens/devices/" + deviceId + "/state"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(activityPath + "/screens/devices/" + deviceId + "/state")
            .header("Authorization", "Bearer " + deviceToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/screens/devices").header("Authorization", "Bearer " + deviceToken))
        .andExpect(status().isForbidden());
  }

  private MvcResult login(String username, String password) throws Exception {
    return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
  }

  @Test
  void activityCredentialsCannotTakeOverGlobalOrSharedAccounts() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String path = "/api/activities/" + activityId + "/memberships";
    String scopedToken = accessToken(login("activity-admin", "ChangeMe!2026"));
    MvcResult systemLogin = login("sysadmin", "ChangeMe!2026");
    String systemId = objectMapper.readTree(systemLogin.getResponse().getContentAsString()).get("userId").asText();
    String systemToken = accessToken(systemLogin);
    mvc.perform(post(path).header("Authorization", "Bearer " + scopedToken).contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":\"" + systemId + "\",\"role\":\"STAFF\"}")).andExpect(status().isCreated());
    try {
      mvc.perform(patch(path + "/users/" + systemId).header("Authorization", "Bearer " + scopedToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"username\":\"sysadmin\",\"displayName\":\"Taken over\",\"password\":\"Attacker!2026\"}"))
          .andExpect(status().isForbidden());
      login("sysadmin", "ChangeMe!2026");
    } finally {
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "/" + systemId)
          .header("Authorization", "Bearer " + systemToken)).andExpect(status().isNoContent());
    }
    String username = "scoped-" + UUID.randomUUID();
    MvcResult member = mvc.perform(post(path + "/users").header("Authorization", "Bearer " + scopedToken)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
            "username", username, "displayName", "Scoped", "password", "Initial!2026", "role", "STAFF"))))
        .andExpect(status().isCreated()).andReturn();
    String userId = objectMapper.readTree(member.getResponse().getContentAsString()).get("userId").asText();
    String update = objectMapper.writeValueAsString(java.util.Map.of("username", username,
        "displayName", "Updated", "password", "ScopedNew!2026"));
    mvc.perform(patch(path + "/users/" + userId).header("Authorization", "Bearer " + scopedToken)
        .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
    login(username, "ScopedNew!2026");
    MvcResult activity = mvc.perform(post("/api/activities").header("Authorization", "Bearer " + systemToken)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Shared identity test\",\"city\":\"Shanghai\"}"))
        .andExpect(status().isCreated()).andReturn();
    String otherId = objectMapper.readTree(activity.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/activities/" + otherId + "/memberships").header("Authorization", "Bearer " + systemToken)
        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + userId + "\",\"role\":\"STAFF\"}"))
        .andExpect(status().isCreated());
    mvc.perform(patch(path + "/users/" + userId).header("Authorization", "Bearer " + scopedToken)
        .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
  }

  @Test
  void refreshReplayCommitsFamilyRevocation() {
    var first = auth.login(new com.matrixlive.security.auth.AuthModels.LoginRequest("sysadmin", "ChangeMe!2026"), null);
    var second = auth.refresh(first.refreshToken(), null);
    expireRefreshGrace(first.refreshToken());
    org.junit.jupiter.api.Assertions.assertThrows(com.matrixlive.service.DomainException.class,
        () -> auth.refresh(first.refreshToken(), null));
    org.junit.jupiter.api.Assertions.assertThrows(com.matrixlive.service.DomainException.class,
        () -> auth.refresh(second.refreshToken(), null));
  }

  @Test
  void concurrentRefreshCannotLeaveASecondActiveBranch() throws Exception {
    var first = auth.login(new com.matrixlive.security.auth.AuthModels.LoginRequest("sysadmin", "ChangeMe!2026"), null);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Boolean> refresh = () -> {
        ready.countDown();
        start.await();
        try { auth.refresh(first.refreshToken(), null); return true; }
        catch (com.matrixlive.service.DomainException exception) { return false; }
      };
      var one = executor.submit(refresh);
      var two = executor.submit(refresh);
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      int successes = (one.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
          + (two.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
      org.junit.jupiter.api.Assertions.assertEquals(2, successes);
      String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(first.refreshToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
        UUID family = refreshTokens.findByTokenHash(hash).orElseThrow().getFamilyId();
        org.junit.jupiter.api.Assertions.assertEquals(1, refreshTokens.findByFamilyIdAndRevokedAtIsNull(family).size());
      });
    }
  }

  @Test
  void replayAndConcurrentDescendantRotationCannotLeaveAnOrphanToken() throws Exception {
    var first = auth.login(new com.matrixlive.security.auth.AuthModels.LoginRequest("sysadmin", "ChangeMe!2026"), null);
    var second = auth.refresh(first.refreshToken(), null);
    expireRefreshGrace(first.refreshToken());
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Void> replay = () -> {
        ready.countDown(); start.await();
        try { auth.refresh(first.refreshToken(), null); org.junit.jupiter.api.Assertions.fail("Replay must fail"); }
        catch (com.matrixlive.service.DomainException expected) { }
        return null;
      };
      java.util.concurrent.Callable<Void> rotate = () -> {
        ready.countDown(); start.await();
        try { auth.refresh(second.refreshToken(), null); }
        catch (com.matrixlive.service.DomainException expected) { }
        return null;
      };
      var one = executor.submit(replay);
      var two = executor.submit(rotate);
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      one.get(15, java.util.concurrent.TimeUnit.SECONDS);
      two.get(15, java.util.concurrent.TimeUnit.SECONDS);
      String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(first.refreshToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
        UUID family = refreshTokens.findByTokenHash(hash).orElseThrow().getFamilyId();
        org.junit.jupiter.api.Assertions.assertTrue(refreshTokens.findByFamilyIdAndRevokedAtIsNull(family).isEmpty());
      });
    }
  }

  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private void expireRefreshGrace(String raw) {
    new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      try {
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var token = refreshTokens.findByTokenHash(hash).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "revokedAt", java.time.Instant.now().minusSeconds(20));
        refreshTokens.saveAndFlush(token);
      } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    });
  }

  private String accessToken(MvcResult result) throws Exception {
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("accessToken").asText();
  }
}
