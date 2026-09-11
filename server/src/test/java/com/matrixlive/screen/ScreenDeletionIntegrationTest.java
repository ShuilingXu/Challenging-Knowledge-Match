package com.matrixlive.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScreenDeletionIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private ActivityRepository activities;
  @Autowired private ScreenDeviceRepository devices;

  @Test
  void deletingADeviceInvalidatesItsSessionAndUnusedPairingLink() throws Exception {
    UUID activityId = activities.save(new Activity("Delete screen", "Shanghai", "DRAFT", Instant.now())).getId();
    String adminToken = login("sysadmin");
    String base = "/api/activities/" + activityId + "/screens/devices";
    JsonNode registration = json.readTree(mvc.perform(post(base + "/register")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Delete me\",\"viewportWidth\":1920,\"viewportHeight\":1080}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String deviceId = registration.at("/device/id").asText();
    String devicePath = base + "/" + deviceId;
    String sessionToken = json.readTree(mvc.perform(post(devicePath + "/session")
            .header("X-Screen-Pairing", registration.get("pairingToken").asText()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    String unusedPairing = json.readTree(mvc.perform(post(devicePath + "/pairing-token")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("pairingToken").asText();

    mvc.perform(delete(devicePath).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());
    assertFalse(devices.existsById(UUID.fromString(deviceId)));
    mvc.perform(get(devicePath + "/state").header("Authorization", "Bearer " + sessionToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(devicePath + "/heartbeat").header("Authorization", "Bearer " + sessionToken)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(devicePath + "/session").header("X-Screen-Pairing", unusedPairing))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(devicePath).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletionIsScopedToTheActivityAndRequiresAdministratorAccess() throws Exception {
    UUID activityId = activities.save(new Activity("Screen scope", "Shanghai", "DRAFT", Instant.now())).getId();
    UUID otherId = activities.save(new Activity("Other screen scope", "Shanghai", "DRAFT", Instant.now())).getId();
    String adminToken = login("sysadmin");
    JsonNode registration = json.readTree(mvc.perform(post("/api/activities/" + activityId + "/screens/devices/register")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Keep me\",\"viewportWidth\":1920,\"viewportHeight\":1080}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String deviceId = registration.at("/device/id").asText();
    mvc.perform(delete("/api/activities/" + otherId + "/screens/devices/" + deviceId)
            .header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/activities/" + activityId + "/screens/devices/" + deviceId)
            .header("Authorization", "Bearer " + login("event-staff"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/activities/" + activityId + "/screens/devices/" + deviceId)
            .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
  }

  private String login(String username) throws Exception {
    return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "password", "ChangeMe!2026"))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
  }
}
