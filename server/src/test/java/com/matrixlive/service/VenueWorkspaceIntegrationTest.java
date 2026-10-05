package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matrixlive.screen.ScreenService;
import com.matrixlive.security.JwtTokenService;
import com.matrixlive.security.auth.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VenueWorkspaceIntegrationTest {
  @Autowired ActivityService service;
  @Autowired TurtleSoupService soups;
  @Autowired ScreenService screens;
  @Autowired MockMvc mvc;
  @Autowired JwtTokenService tokens;
  @Autowired UserAccountRepository users;
  @Autowired ActivityMembershipRepository memberships;
  @Autowired com.matrixlive.security.StompJwtChannelInterceptor socket;

  private ActivityResponse create(String type, UUID parent) {
    return service.createActivity(new CreateActivityRequest(type+UUID.randomUUID(), "Shanghai", Instant.now(),
        null,null,null,null,null,null,parent,type));
  }
  private QuestionAdminResponse question(UUID activity, String title) {
    return service.createQuestion(activity, new QuestionWriteRequest("SINGLE",title,List.of("A","B"),Set.of("A"),10,0,null,40,true));
  }

  @Test void registrationSettingsAndPeopleAreIsolatedAndMotherCannotDisplayOrRegister() {
    var main=create("EVENT",null); var a=create("VENUE",main.id()); var b=create("VENUE",main.id());
    service.changeActivityStatus(main.id(),new ChangeActivityStatusRequest("LIVE"));
    service.changeActivityStatus(a.id(),new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    service.changeActivityStatus(b.id(),new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    service.createRegistrationField(a.id(),new RegistrationFieldRequest("team","Team","TEXT",List.of(),false,0));
    assertTrue(service.registrationFields(b.id()).isEmpty());
    var pa=service.register(a.id(),"main",new RegisterParticipantRequest("Alice","same-contact",null));
    var pb=service.register(b.id(),"main",new RegisterParticipantRequest("Bob","same-contact",null));
    assertNotEquals(pa.id(),pb.id());
    assertEquals(List.of(pa.id()),service.listParticipants(a.id(),null).stream().map(ParticipantResponse::id).toList());
    assertThrows(DomainException.class,()->service.participant(b.id(),pa.id()));
    assertThrows(DomainException.class,()->service.register(main.id(),"main",new RegisterParticipantRequest("Invalid","other",null)));
    assertThrows(DomainException.class,()->screens.listDevices(main.id()));
    service.changeActivityStatus(main.id(),new ChangeActivityStatusRequest("PAUSED"));
    assertThrows(DomainException.class,()->service.register(a.id(),"main",new RegisterParticipantRequest("Blocked","new-contact",null)));
  }

  @Test void selectedQuestionsCopyIndependentlyAndResyncKeepsLocalDisabledState() {
    var main=create("EVENT",null); var a=create("VENUE",main.id()); var b=create("VENUE",main.id());
    var qa=create("QUIZ",a.id()); var qb=create("QUIZ",b.id());
    var source=question(main.id(),"Original"); question(main.id(),"Not selected");
    var ca=service.syncQuestions(qa.id(),new SyncQuestionsRequest(List.of(source.id()))).getFirst();
    var cb=service.syncQuestions(qb.id(),new SyncQuestionsRequest(List.of(source.id()))).getFirst();
    assertNotEquals(ca.id(),cb.id()); assertNotEquals(ca.id(),source.id());
    service.updateQuestion(qa.id(),ca.id(),new QuestionWriteRequest(null,"Local",null,null,null,null,null,null,false));
    assertEquals("Original",service.listQuestionAdministration(qb.id()).getFirst().title());
    assertEquals("Original",service.listQuestionAdministration(main.id()).getFirst().title());
    var again=service.syncQuestions(qa.id(),new SyncQuestionsRequest(List.of(source.id()))).getFirst();
    assertEquals(ca.id(),again.id()); assertFalse(again.enabled());
    assertEquals(1,service.listQuestionAdministration(qa.id()).size());
    assertThrows(DomainException.class,()->service.syncQuestions(qa.id(),new SyncQuestionsRequest(List.of(cb.id()))));
    assertEquals(1,service.listQuestionAdministration(qa.id()).size());
  }

  @Test void randomOrderIsSavedAndIdenticalAcrossRepeatedReads() {
    var main=create("EVENT",null); var venue=create("VENUE",main.id()); var quiz=create("QUIZ",venue.id());
    List<UUID> ids=new ArrayList<>(); for(int i=0;i<12;i++) ids.add(question(quiz.id(),"Question "+i).id());
    var set=service.createQuestionSet(quiz.id(),new QuestionSetRequest("Random",null,ids,true,true));
    var order=set.items().stream().map(QuestionSetItemResponse::questionId).toList();
    assertEquals(new HashSet<>(ids),new HashSet<>(order)); assertEquals(ids.size(),order.size());
    assertEquals(order,service.listQuestions(quiz.id()).stream().map(QuestionResponse::id).toList());
    assertEquals(order,service.listQuestions(quiz.id()).stream().map(QuestionResponse::id).toList());
  }

  @Test void motherSoupLibrarySupportsMultipleScriptsAndCopiesAreIndependent() {
    var main=create("EVENT",null); var venue=create("VENUE",main.id());
    var a=create("TURTLE_SOUP",venue.id()); var b=create("TURTLE_SOUP",venue.id());
    var template=soups.saveTemplate(main.id(),null,new TurtleSoupService.Content("First","Surface",List.of("Clue"),"Secret"));
    soups.saveTemplate(main.id(),null,new TurtleSoupService.Content("Second","Another surface",List.of(),"Another secret"));
    assertEquals(2,soups.templates(a.id()).size());
    soups.syncTemplate(a.id(),template.id()); soups.syncTemplate(b.id(),template.id());
    soups.save(a.id(),new TurtleSoupService.Content("Local","Local surface",List.of(),"Local solution"));
    assertEquals("First",soups.state(b.id()).content().title());
    assertThrows(DomainException.class,()->soups.saveTemplate(a.id(),template.id(),template.content()));
  }

  @Test void venueMembershipCannotReadOrChangeOtherVenuesOrTheMother() throws Exception {
    var main=create("EVENT",null); var a=create("VENUE",main.id()); var b=create("VENUE",main.id());
    var qa=create("QUIZ",a.id()); var qb=create("QUIZ",b.id());
    var user=users.save(new UserAccount("venue-"+UUID.randomUUID(),"Venue admin","unused",null));
    memberships.save(new ActivityMembership(a.id(),user.getId(),UserRole.ACTIVITY_ADMIN));
    String token=tokens.issueAccountToken(user).value();
    for(UUID id : List.of(a.id(),qa.id())) mvc.perform(get("/api/activities/"+id+"/participants").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    for(UUID id : List.of(main.id(),b.id(),qb.id())) {
      mvc.perform(get("/api/activities/"+id+"/participants").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
      mvc.perform(patch("/api/activities/"+id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Forged\"}")).andExpect(status().isForbidden());
    }
    mvc.perform(post("/api/activities").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Forged child\",\"city\":\"Shanghai\",\"activityType\":\"QUIZ\",\"parentActivityId\":\""+b.id()+"\"}")).andExpect(status().isForbidden());
  }

  @Test void motherAndDirectChildMembershipsCannotBypassVenueAssignments() throws Exception {
    var main=create("EVENT",null); var venue=create("VENUE",main.id()); var quiz=create("QUIZ",venue.id());
    var user=users.save(new UserAccount("mother-"+UUID.randomUUID(),"Mother admin","unused",null));
    memberships.save(new ActivityMembership(main.id(),user.getId(),UserRole.ACTIVITY_ADMIN));
    memberships.save(new ActivityMembership(quiz.id(),user.getId(),UserRole.ACTIVITY_ADMIN));
    String token=tokens.issueAccountToken(user).value();
    mvc.perform(get("/api/activities/"+main.id()+"/questions/admin").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    for(UUID id : List.of(venue.id(),quiz.id())) mvc.perform(get("/api/activities/"+id+"/questions/admin").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  }

  @Test void realtimeSubscriptionsCannotCrossVenueBoundaries() {
    var main=create("EVENT",null); var a=create("VENUE",main.id()); var b=create("VENUE",main.id());
    var qa=create("QUIZ",a.id()); var qb=create("QUIZ",b.id());
    var user=users.save(new UserAccount("socket-venue-"+UUID.randomUUID(),"Venue staff","unused",null));
    memberships.save(new ActivityMembership(a.id(),user.getId(),UserRole.STAFF));
    String session=UUID.randomUUID().toString();
    var connect=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
    connect.setSessionId(session); connect.setNativeHeader("Authorization","Bearer "+tokens.issueAccountToken(user).value()); connect.setLeaveMutable(true);
    socket.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],connect.getMessageHeaders()),null);
    for(UUID id : List.of(qa.id(),qb.id())) {
      var subscribe=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
      subscribe.setSessionId(session); subscribe.setUser(connect.getUser()); subscribe.setDestination("/topic/activities/"+id);
      var message=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],subscribe.getMessageHeaders());
      if(id.equals(qa.id())) assertNotNull(socket.preSend(message,null));
      else assertThrows(org.springframework.security.access.AccessDeniedException.class,()->socket.preSend(message,null));
    }
  }

  @Test void childMemberAdministrationWritesVenueMemberships() throws Exception {
    var main=create("EVENT",null); var venue=create("VENUE",main.id()); var quiz=create("QUIZ",venue.id());
    var admin=users.save(new UserAccount("scope-admin-"+UUID.randomUUID(),"Venue admin","unused",null));
    var staff=users.save(new UserAccount("scope-staff-"+UUID.randomUUID(),"Venue staff","unused",null));
    memberships.save(new ActivityMembership(venue.id(),admin.getId(),UserRole.ACTIVITY_ADMIN));
    mvc.perform(post("/api/activities/"+quiz.id()+"/memberships").header("Authorization","Bearer "+tokens.issueAccountToken(admin).value())
        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\""+staff.getId()+"\",\"role\":\"STAFF\"}")).andExpect(status().isCreated());
    assertTrue(memberships.findByUserIdAndActivityId(staff.getId(),venue.id()).isPresent());
    assertTrue(memberships.findByUserIdAndActivityId(staff.getId(),quiz.id()).isEmpty());
  }

  @Autowired com.matrixlive.repository.ActivityRepository activityRepository;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

  private ActivityResponse venueWithFeatures(UUID main, List<String> features) throws Exception {
    return service.createActivity(mapper.readValue(mapper.writeValueAsString(Map.of("name","Venue "+UUID.randomUUID(),
        "city","Shanghai","activityType","VENUE","parentActivityId",main,"features",features)),CreateActivityRequest.class));
  }
  private void changeFeatures(UUID venue, List<String> features) throws Exception {
    service.updateActivity(venue,mapper.readValue(mapper.writeValueAsString(Map.of("features",features)),UpdateActivityRequest.class));
  }
  private UUID featureId(UUID venue, String type) {
    return activityRepository.findByParentActivityId(venue).stream().filter(a -> type.equals(a.getActivityType())).findFirst().orElseThrow().getId();
  }
  @Test void creationCreatesOnlySelectedVenueFeaturesAndReenablePreservesData() throws Exception {
    var main=create("EVENT",null);var venue=venueWithFeatures(main.id(),List.of("QUIZ","TURTLE_SOUP"));
    assertEquals(2,activityRepository.findByParentActivityId(venue.id()).size());
    UUID quiz=featureId(venue.id(),"QUIZ"); var original=question(quiz,"Retained question");
    changeFeatures(venue.id(),List.of("TURTLE_SOUP","LOTTERY"));
    assertFalse(activityRepository.findById(quiz).orElseThrow().isFeatureEnabled());
    assertTrue(service.listActivities().stream().anyMatch(a -> a.id().equals(venue.id())));
    assertThrows(DomainException.class,()->service.listQuestions(quiz));
    assertThrows(DomainException.class,()->screens.listDevices(quiz));
    changeFeatures(venue.id(),List.of("QUIZ"));
    assertEquals(quiz,featureId(venue.id(),"QUIZ"));
    assertEquals(original.id(),service.listQuestionAdministration(quiz).getFirst().id());
    assertEquals(3,activityRepository.findByParentActivityId(venue.id()).size());
    assertThrows(DomainException.class,()->changeFeatures(venue.id(),List.of("BOGUS")));
  }
  @Test void disabledFeatureIsDeniedEvenToItsAdministratorOverHttpAndRealtime() throws Exception {
    var main=create("EVENT",null);var venue=venueWithFeatures(main.id(),List.of("QUIZ"));UUID quiz=featureId(venue.id(),"QUIZ");
    var user=users.save(new UserAccount("switch-"+UUID.randomUUID(),"Venue admin","unused",null));
    memberships.save(new ActivityMembership(venue.id(),user.getId(),UserRole.ACTIVITY_ADMIN));
    String token=tokens.issueAccountToken(user).value();
    changeFeatures(venue.id(),List.of());
    mvc.perform(get("/api/activities/"+quiz+"/control").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    mvc.perform(post("/api/activities/"+quiz+"/status").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LIVE\"}")).andExpect(status().isForbidden());
    mvc.perform(get("/api/activities/"+venue.id()+"/participants").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    String session=UUID.randomUUID().toString();
    var connect=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
    connect.setSessionId(session);connect.setNativeHeader("Authorization","Bearer "+token);connect.setLeaveMutable(true);
    socket.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],connect.getMessageHeaders()),null);
    var subscribe=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
    subscribe.setSessionId(session);subscribe.setDestination("/topic/activities/"+quiz);subscribe.setLeaveMutable(true);
    assertThrows(org.springframework.security.access.AccessDeniedException.class,()->socket.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],subscribe.getMessageHeaders()),null));
  }
  @Test void multipleSoupScriptsSwitchSafelyAndKeepLocalCopiesIndependent() throws Exception {
    var main=create("EVENT",null);var venue=venueWithFeatures(main.id(),List.of("TURTLE_SOUP"));
    var other=venueWithFeatures(main.id(),List.of("TURTLE_SOUP"));UUID soup=featureId(venue.id(),"TURTLE_SOUP"),second=featureId(other.id(),"TURTLE_SOUP");
    var a=soups.saveTemplate(main.id(),null,new TurtleSoupService.Content("One","Surface one",List.of("Clue"),"Secret one"));
    var b=soups.saveTemplate(main.id(),null,new TurtleSoupService.Content("Two","Surface two",List.of(),"Secret two"));
    soups.configurePlaylist(soup,List.of(a.id(),b.id()));soups.configurePlaylist(second,List.of(a.id()));
    soups.save(soup,new TurtleSoupService.Content("Local one","Edited",List.of("Edited clue"),"Local secret"));
    assertEquals("One",soups.state(second).content().title());assertEquals("One",soups.templates(main.id()).getFirst().content().title());
    service.changeActivityStatus(main.id(),new ChangeActivityStatusRequest("LIVE"));
    service.changeActivityStatus(venue.id(),new ChangeActivityStatusRequest("LIVE"));
    service.changeActivityStatus(soup,new ChangeActivityStatusRequest("LIVE"));
    var shown=soups.control(soup,new TurtleSoupService.Command("SHOW_SURFACE",soups.state(soup).revision()));
    assertThrows(DomainException.class,()->soups.switchTemplate(soup,new TurtleSoupService.SwitchRequest(b.id(),shown.revision())));
    var reset=soups.control(soup,new TurtleSoupService.Command("RESET",shown.revision()));
    var next=soups.switchTemplate(soup,new TurtleSoupService.SwitchRequest(b.id(),reset.revision()));
    assertEquals("Two",next.content().title());assertEquals("LOBBY",next.stage());assertTrue(next.revealedClueIndexes().isEmpty());
    assertFalse(TurtleSoupService.display(next).containsKey("solution"));assertFalse(TurtleSoupService.display(next).containsKey("surface"));
    assertThrows(DomainException.class,()->soups.switchTemplate(soup,new TurtleSoupService.SwitchRequest(a.id(),reset.revision())));
    var back=soups.switchTemplate(soup,new TurtleSoupService.SwitchRequest(a.id(),next.revision()));assertEquals("Local one",back.content().title());
    var foreign=create("EVENT",null);var foreignTemplate=soups.saveTemplate(foreign.id(),null,a.content());
    assertThrows(DomainException.class,()->soups.configurePlaylist(soup,List.of(foreignTemplate.id())));
    assertEquals(2,soups.playlist(soup).entries().size());
  }
}
