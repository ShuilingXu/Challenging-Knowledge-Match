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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityHierarchyIntegrationTest {
  @Autowired private ActivityService service;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserAccountRepository users;
  @Autowired private ActivityMembershipRepository memberships;

  @Test
  void lotterySharesMainRegistrationButKeepsItsChancesAndAwardsSeparate() throws Exception {
    var parent = mainActivity();
    var lottery = lottery(parent.id());
    service.createVenue(parent.id(), new VenueRequest("main", "Main Hall", 10, true));
    service.createRegistrationField(parent.id(), new RegistrationFieldRequest("team", "Team", "TEXT", List.of(), true, 0));
    var person = service.register(lottery.id(), "main",
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
            .content(mapper.writeValueAsString(Map.of("activityId", lottery.id(), "venue", "main", "contact", person.contact()))))
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
