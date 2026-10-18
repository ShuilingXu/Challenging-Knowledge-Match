package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.security.JwtTokenService;
import com.matrixlive.security.auth.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CheckinIntegrationTest {
  @Autowired ActivityService activities;
  @Autowired CheckinService checkin;
  @Autowired MockMvc mvc;
  @Autowired JwtTokenService jwt;
  @Autowired UserAccountRepository users;
  @Autowired ActivityMembershipRepository memberships;
  @Autowired ObjectMapper json;
  @Autowired HumanChallengeRepository challenges;
  @Autowired JdbcTemplate db;
  private ActivityResponse venue() {
    var main=activities.createActivity(new CreateActivityRequest("Ticket event "+UUID.randomUUID(),"Shanghai",Instant.now()));
    activities.changeActivityStatus(main.id(),new ChangeActivityStatusRequest("LIVE"));
    var venue=activities.createActivity(new CreateActivityRequest("Ticket venue "+UUID.randomUUID(),"Shanghai",Instant.now(),null,null,null,null,null,null,main.id(),"VENUE"));
    activities.changeActivityStatus(venue.id(),new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    return venue;
  }
  private CheckinService.Entry entry(ActivityResponse a,String contact,String type) { return new CheckinService.Entry("昵称",contact,a.id().toString(),type,"测试备注"); }
  private String admin() { return jwt.issueAccountToken(users.findByUsernameIgnoreCase("sysadmin").orElseThrow()).value(); }
  private String staff(UUID id) {
    var user=users.findByUsernameIgnoreCase("event-staff").orElseThrow();
    memberships.saveAndFlush(new ActivityMembership(id,user.getId(),UserRole.STAFF));
    return jwt.issueAccountToken(user).value();
  }
  private String path(UUID id) { return "/api/activities/"+id+"/checkin"; }
  private int redeem(UUID id,String token,String access,UUID request) throws Exception {
    return mvc.perform(post(path(id)+"/redeem").header("Authorization","Bearer "+access).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new CheckinService.Redeem(token,request)))).andReturn().getResponse().getStatus();
  }
  @Test void importedPeopleLinkToParticipantsAndRestrictedRegistrationUpdatesNicknameEvenAtCapacity() {
    var a=venue(); checkin.registration(a.id(),false);
    var ticket=checkin.create(a.id(),List.of(entry(a,"001-234 567","单次"))).getFirst();
    assertEquals("001234567",ticket.contact());
    assertEquals(0,ticket.checkinCount());
    assertEquals(ticket.participantId(),activities.listParticipants(a.id(),null).getFirst().id());
    var registrationVenue=activities.listVenues(a.id()).getFirst();
    activities.updateVenue(a.id(),registrationVenue.id(),new UpdateVenueRequest(null,1,null));
    var again=activities.register(a.id(),registrationVenue.code(),new RegisterParticipantRequest("新昵称","001234567",null));
    assertEquals(ticket.participantId(),again.id()); assertEquals("新昵称",again.name());
    assertEquals("新昵称",checkin.list(a.id()).getFirst().name());
    assertFalse(activities.activity(a.id()).registrationAllowed());
    assertThrows(DomainException.class,()->activities.register(a.id(),registrationVenue.code(),new RegisterParticipantRequest("新人","new-contact",null)));
    checkin.registration(a.id(),true);
    assertThrows(DomainException.class,()->activities.register(a.id(),registrationVenue.code(),new RegisterParticipantRequest("新人","new-contact",null)));
    var b=venue(); assertDoesNotThrow(()->activities.register(b.id(),"main",new RegisterParticipantRequest("其他场次","001234567",null)));
  }
  @Test void batchIsAtomicAndRejectsDuplicateContactsOrWrongScene() {
    var a=venue();
    assertThrows(DomainException.class,()->checkin.create(a.id(),List.of(entry(a,"phone001","SINGLE"),entry(a,"phone-001","MULTI"))));
    assertTrue(checkin.list(a.id()).isEmpty()); assertTrue(activities.listParticipants(a.id(),null).isEmpty());
    assertThrows(DomainException.class,()->checkin.create(a.id(),List.of(entry(a,"phone002","SINGLE"),new CheckinService.Entry("Other","phone003","wrong","SINGLE",""))));
    assertTrue(activities.listParticipants(a.id(),null).isEmpty());
    checkin.create(a.id(),List.of(entry(a,"phone001","SINGLE")));
    assertThrows(DomainException.class,()->checkin.create(a.id(),List.of(entry(a,"new-first","SINGLE"),entry(a,"phone001","MULTI"))));
    assertEquals(1,activities.listParticipants(a.id(),null).size());
  }
  @Test void staffCanReadAndRedeemButOnlyAdminsCreateOrControlRegistrationAndScopesAreIsolated() throws Exception {
    var a=venue(); var b=venue(); String staff=staff(a.id()); String admin=admin();
    String body=json.writeValueAsString(Map.of("entries",List.of(entry(a,"staff-test","SINGLE"))));
    mvc.perform(post(path(a.id())).header("Authorization","Bearer "+staff).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    mvc.perform(put(path(a.id())+"/registration").header("Authorization","Bearer "+staff).contentType(MediaType.APPLICATION_JSON).content("{\"registrationAllowed\":false}")).andExpect(status().isForbidden());
    mvc.perform(post(path(a.id())).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    var t=checkin.list(a.id()).getFirst();
    mvc.perform(get(path(a.id())).header("Authorization","Bearer "+staff)).andExpect(status().isOk());
    mvc.perform(get(path(b.id())).header("Authorization","Bearer "+staff)).andExpect(status().isForbidden());
    mvc.perform(get(path(a.id()))).andExpect(status().isUnauthorized());
    String participant=jwt.issueParticipantToken(a.id(),t.participantId()).value();
    mvc.perform(get(path(a.id())).header("Authorization","Bearer "+participant)).andExpect(status().isForbidden());
    assertEquals(403,redeem(a.id(),t.token(),participant,UUID.randomUUID()));
    assertEquals(409,redeem(b.id(),t.token(),admin,UUID.randomUUID()));
    UUID request=UUID.randomUUID(); assertEquals(200,redeem(a.id(),t.token(),staff,request)); assertEquals(200,redeem(a.id(),t.token(),staff,request));
    assertEquals(409,redeem(a.id(),t.token(),staff,UUID.randomUUID())); assertEquals(1,checkin.list(a.id()).getFirst().checkinCount());
    assertEquals(1,db.queryForObject("select count(*) from ticket_redemptions where ticket_id=?",Integer.class,t.id()));
  }
  @Test void singleUseIsSafeUnderConcurrentScansAndMultiUseHasNoLimit() throws Exception {
    var a=venue(); String access=staff(a.id());
    var single=checkin.create(a.id(),List.of(entry(a,"concurrent","SINGLE"))).getFirst();
    var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
    Callable<Integer> action=()-> { ready.countDown(); start.await(); return redeem(a.id(),single.token(),access,UUID.randomUUID()); };
    try { var first=pool.submit(action); var second=pool.submit(action); assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown(); var statuses=new ArrayList<>(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))); Collections.sort(statuses); assertEquals(List.of(200,409),statuses); } finally { pool.shutdownNow(); }
    var multi=checkin.create(a.id(),List.of(entry(a,"unlimited","MULTI"))).stream().filter(t->t.type().equals("MULTI")).findFirst().orElseThrow();
    for(int i=0;i<5;i++) assertEquals(200,redeem(a.id(),multi.token(),access,UUID.randomUUID()));
    assertEquals(5,checkin.list(a.id()).stream().filter(t->t.id().equals(multi.id())).findFirst().orElseThrow().checkinCount());
    assertEquals(5,db.queryForObject("select count(*) from ticket_redemptions where ticket_id=?",Integer.class,multi.id()));
  }
  private org.springframework.test.web.servlet.ResultActions join(ActivityResponse a,String contact,String name,UUID challenge,String answer) throws Exception {
    return mvc.perform(post("/api/auth/participant-join").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("activityId",a.id(),"venue","main","registration",Map.of("name",name,"contact",contact),"challengeId",challenge,"challengeAnswer",answer))));
  }
  @Test void unifiedJoinChecksCaptchaBeforeMutationUpdatesExistingAndRespectsAdmissionFlag() throws Exception {
    var a=venue(); checkin.registration(a.id(),false);
    var ticket=checkin.create(a.id(),List.of(entry(a,"join-contact","SINGLE"))).getFirst();
    UUID invalid=TestChallenges.create(challenges);
    join(a,"join-contact","非法变更",invalid,"WRONG").andExpect(status().isUnauthorized());
    assertEquals("昵称",activities.participant(a.id(),ticket.participantId()).name());
    join(a,"join-contact","重放验证码",invalid,"ABCDE").andExpect(status().isUnauthorized());
    var result=join(a,"join-contact","报名新昵称",TestChallenges.create(challenges),"ABCDE").andExpect(status().isOk()).andReturn();
    assertEquals(ticket.participantId().toString(),json.readTree(result.getResponse().getContentAsString()).path("participantId").asText());
    assertEquals("报名新昵称",activities.participant(a.id(),ticket.participantId()).name());
    join(a,"not-in-list","新人",TestChallenges.create(challenges),"ABCDE").andExpect(status().isConflict());
    checkin.registration(a.id(),true);
    join(a,"new-admission","新人",TestChallenges.create(challenges),"ABCDE").andExpect(status().isOk());
    mvc.perform(post("/api/activities/"+a.id()+"/venues/main/registrations").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"绕过验证码\",\"contact\":\"join-contact\"}")).andExpect(status().isUnauthorized());
  }
}
