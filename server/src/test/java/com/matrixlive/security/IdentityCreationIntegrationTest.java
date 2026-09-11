package com.matrixlive.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
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
class IdentityCreationIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private ActivityRepository activities;
  @Autowired private UserAccountRepository users;
  @Autowired private ActivityMembershipRepository memberships;

  @Test
  void createsASystemAdministratorWhoCanLogInAndManageAccounts() throws Exception {
    String adminToken = login("sysadmin", "ChangeMe!2026");
    String username = "system-" + UUID.randomUUID();
    JsonNode created = json.readTree(mvc.perform(post("/api/admin/users")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "displayName", "New administrator",
                "password", "AccountPass!2026", "systemRole", "SYSTEM_ADMIN"))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    assertEquals("SYSTEM_ADMIN", created.get("systemRole").asText());
    String createdToken = login(username, "AccountPass!2026");
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + createdToken))
        .andExpect(status().isOk());
  }

  @Test
  void systemAndActivityAdministratorsCanAtomicallyCreateActivityAdministrators() throws Exception {
    UUID activityId = memberships.findAll().stream()
        .filter(member -> member.getUserId().equals(users.findByUsernameIgnoreCase("activity-admin").orElseThrow().getId()))
        .findFirst().orElseThrow().getActivityId();
    for (String creator : new String[] { "sysadmin", "activity-admin" }) {
      String username = "activity-" + UUID.randomUUID();
      JsonNode member = json.readTree(mvc.perform(post("/api/activities/" + activityId + "/memberships/users")
              .header("Authorization", "Bearer " + login(creator, "ChangeMe!2026"))
              .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                  "username", username, "displayName", "Activity administrator", "password", "AccountPass!2026",
                  "role", "ACTIVITY_ADMIN"))))
          .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
      UUID userId = UUID.fromString(member.get("userId").asText());
      assertEquals(UserRole.ACTIVITY_ADMIN, memberships.findByUserIdAndActivityId(userId, activityId).orElseThrow().getRole());
      assertTrue(users.findById(userId).orElseThrow().getSystemRole() == null);
      mvc.perform(get("/api/activities/" + activityId + "/memberships")
              .header("Authorization", "Bearer " + login(username, "AccountPass!2026")))
          .andExpect(status().isOk());
    }
  }

  @Test
  void invalidActivityRoleDoesNotLeaveAnOrphanAccount() throws Exception {
    UUID activityId = activities.findAll().getFirst().getId();
    String username = "invalid-" + UUID.randomUUID();
    mvc.perform(post("/api/activities/" + activityId + "/memberships/users")
            .header("Authorization", "Bearer " + login("sysadmin", "ChangeMe!2026"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "username", username, "displayName", "Invalid role", "password", "AccountPass!2026", "role", "SYSTEM_ADMIN"))))
        .andExpect(status().isBadRequest());
    assertFalse(users.findByUsernameIgnoreCase(username).isPresent());
  }

  @Test
  void invalidCredentialsAndDuplicateNamesReturnActionableClientErrors() throws Exception {
    String adminToken = login("sysadmin", "ChangeMe!2026");
    mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "username", "short-password", "displayName", "Invalid password", "password", "short", "systemRole", "SYSTEM_ADMIN"))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "username", " SYSADMIN ", "displayName", "Duplicate", "password", "AccountPass!2026", "systemRole", "SYSTEM_ADMIN"))))
        .andExpect(status().isConflict());
  }

  private String login(String username, String password) throws Exception {
    return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "password", password))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
  }
}
