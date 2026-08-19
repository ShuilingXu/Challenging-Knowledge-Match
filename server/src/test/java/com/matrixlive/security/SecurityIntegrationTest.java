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

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ActivityRepository activities;

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
            .content("{\"stage\":\"QUESTION\",\"seconds\":30}"))
        .andExpect(status().isUnauthorized());

    MvcResult participantSession = mvc.perform(post("/api/auth/participant-token").contentType(MediaType.APPLICATION_JSON)
            .content("{\"activityId\":\"" + activityId + "\",\"venue\":\"south\",\"contact\":\"13800002048\"}"))
        .andExpect(status().isOk()).andReturn();
    String participantToken = accessToken(participantSession);

    mvc.perform(get(activityPath + "/questions").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/control").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/questions/control").header("Authorization", "Bearer " + participantToken))
        .andExpect(status().isForbidden());
    mvc.perform(post(activityPath + "/control").header("Authorization", "Bearer " + participantToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"QUESTION\",\"seconds\":30}"))
        .andExpect(status().isForbidden());

    String staffToken = accessToken(login("event-staff", "ChangeMe!2026"));
    mvc.perform(get(activityPath + "/questions/control").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(get(activityPath + "/questions/admin").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(post(activityPath + "/control").header("Authorization", "Bearer " + staffToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"QUESTION\",\"seconds\":30}"))
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

  private String accessToken(MvcResult result) throws Exception {
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("accessToken").asText();
  }
}
