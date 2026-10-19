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

  @Test void questionSetsSyncInOrderReuseSharedQuestionsAndResyncWithoutDuplicates() {
    var main=create("EVENT",null); var venue=create("VENUE",main.id()); var other=create("VENUE",main.id());
    var quiz=create("QUIZ",venue.id()); var otherQuiz=create("QUIZ",other.id());
    var first=question(main.id(),"First"); var second=question(main.id(),"Second");
    var source=service.createQuestionSet(main.id(),new QuestionSetRequest("Set one","Description",List.of(second.id(),first.id()),true,false));
    var shared=service.createQuestionSet(main.id(),new QuestionSetRequest("Set two",null,List.of(first.id()),false,false));
    assertEquals(2,service.sourceQuestionSets(quiz.id()).size());
    var copies=service.syncQuestionSets(quiz.id(),new SyncQuestionSetsRequest(List.of(source.id(),shared.id(),source.id())));
    assertEquals(2,copies.size()); assertFalse(copies.getFirst().active());
    assertEquals(List.of("Second","First"),copies.getFirst().items().stream().map(QuestionSetItemResponse::title).toList());
    assertEquals(2,service.listQuestionAdministration(quiz.id()).size());
    UUID localFirst=copies.getFirst().items().get(1).questionId();
    assertNotEquals(first.id(),localFirst); assertEquals(localFirst,copies.get(1).items().getFirst().questionId());
    service.updateQuestion(quiz.id(),localFirst,new QuestionWriteRequest(null,"Local",null,null,null,null,null,null,false));
    service.activateQuestionSet(quiz.id(),copies.getFirst().id());
    service.updateQuestion(main.id(),first.id(),new QuestionWriteRequest(null,"Updated",null,null,null,null,null,null,null));
    service.updateQuestionSet(main.id(),source.id(),new QuestionSetRequest("Renamed","",List.of(first.id(),second.id()),null,false));
    var again=service.syncQuestionSets(quiz.id(),new SyncQuestionSetsRequest(List.of(source.id()))).getFirst();
    assertEquals(copies.getFirst().id(),again.id()); assertTrue(again.active()); assertNull(again.description());
    assertEquals("Renamed",again.name()); assertEquals(localFirst,again.items().getFirst().questionId());
    assertEquals(List.of("Updated","Second"),again.items().stream().map(QuestionSetItemResponse::title).toList());
    assertEquals(2,service.listQuestionSets(quiz.id()).size());
    assertFalse(service.listQuestionAdministration(quiz.id()).stream().filter(q->q.id().equals(localFirst)).findFirst().orElseThrow().enabled());
    assertTrue(service.listQuestionSets(otherQuiz.id()).isEmpty());
    var otherCopy=service.syncQuestionSets(otherQuiz.id(),new SyncQuestionSetsRequest(List.of(source.id()))).getFirst();
    assertNotEquals(localFirst,otherCopy.items().getFirst().questionId());
  }

  @Test void invalidOrConflictingQuestionSetSyncDoesNotPartiallyCopy() {
    var main=create("EVENT",null);var venue=create("VENUE",main.id());var quiz=create("QUIZ",venue.id());
    var source=service.createQuestionSet(main.id(),new QuestionSetRequest("Source",null,List.of(question(main.id(),"Question").id()),false,false));
    var foreign=create("EVENT",null);
    var foreignSet=service.createQuestionSet(foreign.id(),new QuestionSetRequest("Foreign",null,List.of(question(foreign.id(),"Foreign question").id()),false,false));
    assertThrows(DomainException.class,()->service.syncQuestionSets(quiz.id(),new SyncQuestionSetsRequest(List.of(source.id(),foreignSet.id()))));
    assertTrue(service.listQuestionAdministration(quiz.id()).isEmpty()); assertTrue(service.listQuestionSets(quiz.id()).isEmpty());
    service.createQuestionSet(quiz.id(),new QuestionSetRequest("Source",null,List.of(question(quiz.id(),"Local").id()),false,false));
    assertThrows(DomainException.class,()->service.syncQuestionSets(quiz.id(),new SyncQuestionSetsRequest(List.of(source.id()))));
    assertEquals(1,service.listQuestionAdministration(quiz.id()).size());
    service.changeActivityStatus(main.id(),new ChangeActivityStatusRequest("LIVE"));
    service.changeActivityStatus(venue.id(),new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    service.changeActivityStatus(quiz.id(),new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    assertThrows(DomainException.class,()->service.syncQuestionSets(quiz.id(),new SyncQuestionSetsRequest(List.of(source.id()))));
  }

  @Test void questionSetSyncRequiresAdministrationOfTheTargetVenue() throws Exception {
    var main=create("EVENT",null);var a=create("VENUE",main.id());var b=create("VENUE",main.id());
    var qa=create("QUIZ",a.id());var qb=create("QUIZ",b.id());
    var source=service.createQuestionSet(main.id(),new QuestionSetRequest("Source",null,List.of(question(main.id(),"Question").id()),false,false));
    var user=users.save(new UserAccount("set-admin-"+UUID.randomUUID(),"Admin","unused",null));
    memberships.save(new ActivityMembership(a.id(),user.getId(),UserRole.ACTIVITY_ADMIN));
    String token=tokens.issueAccountToken(user).value();String payload="{\"questionSetIds\":[\""+source.id()+"\"]}";
    mvc.perform(get("/api/activities/"+qa.id()+"/question-sets/source").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    mvc.perform(post("/api/activities/"+qa.id()+"/question-sets/sync").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
    mvc.perform(post("/api/activities/"+qb.id()+"/question-sets/sync").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
    mvc.perform(post("/api/activities/"+qa.id()+"/question-sets/sync").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"questionSetIds\":[]}")).andExpect(status().isBadRequest());
    memberships.findByUserIdAndActivityId(user.getId(),a.id()).ifPresent(memberships::delete);
    memberships.save(new ActivityMembership(a.id(),user.getId(),UserRole.STAFF));
    mvc.perform(get("/api/activities/"+qa.id()+"/question-sets/source").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    mvc.perform(post("/api/activities/"+qa.id()+"/question-sets/sync").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
  }

  @Test void staffSelectsEnabledQuestionSetsButCannotManageAvailability() throws Exception {
    var main = create("EVENT", null);
    var venue = create("VENUE", main.id());
    var quiz = create("QUIZ", venue.id());
    var otherVenue = create("VENUE", main.id());
    var otherQuiz = create("QUIZ", otherVenue.id());
    var first = question(quiz.id(), "First");
    var second = question(quiz.id(), "Second");
    var set = service.createQuestionSet(quiz.id(), new QuestionSetRequest("Enabled", null, List.of(second.id()), false, false, true));
    var disabled = service.createQuestionSet(quiz.id(), new QuestionSetRequest("Disabled", null, List.of(first.id()), false, false, false));
    var user = users.save(new UserAccount("set-host-" + UUID.randomUUID(), "Host", "unused", null));
    memberships.save(new ActivityMembership(venue.id(), user.getId(), UserRole.STAFF));
    String token = tokens.issueAccountToken(user).value();
    String base = "/api/activities/" + quiz.id() + "/question-sets";
    mvc.perform(get(base).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    mvc.perform(post(base + "/" + set.id() + "/activate").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    assertEquals(List.of(second.id()), service.listQuestionControl(quiz.id()).stream().map(QuestionControlResponse::id).toList());
    assertEquals(List.of(second.id()), service.listQuestions(quiz.id()).stream().map(QuestionResponse::id).toList());
    mvc.perform(post(base + "/" + disabled.id() + "/activate").header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
    mvc.perform(put(base + "/" + disabled.id()).header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Disabled\",\"enabled\":true}")).andExpect(status().isForbidden());
    mvc.perform(post(base).header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}")).andExpect(status().isForbidden());
    mvc.perform(delete(base + "/" + set.id()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    mvc.perform(post("/api/activities/" + otherQuiz.id() + "/question-sets/" + set.id() + "/activate")
        .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    var stopped = service.updateQuestionSet(quiz.id(), set.id(), new QuestionSetRequest("Enabled", null, null, null, false, false));
    assertFalse(stopped.enabled());
    assertFalse(stopped.active());
    assertNull(service.activity(quiz.id()).activeQuestionSetId());
    assertEquals("LOBBY", service.controlState(quiz.id()).stage());
    var enabled = service.updateQuestionSet(quiz.id(), set.id(), new QuestionSetRequest("Enabled", null, null, null, false, true));
    assertTrue(enabled.enabled());
    assertFalse(enabled.active());
  }

  @Test void switchingQuestionSetsRequiresClosingTheCurrentQuestion() {
    var quiz = QuizTestSupport.create(service, new CreateActivityRequest("Switch sets", "Shanghai", Instant.now()));
    var first = question(quiz.id(), "First");
    var second = question(quiz.id(), "Second");
    var set = service.createQuestionSet(quiz.id(), new QuestionSetRequest("First set", null, List.of(first.id()), true));
    var next = service.createQuestionSet(quiz.id(), new QuestionSetRequest("Next set", null, List.of(second.id()), false));
    QuizTestSupport.start(service, quiz.id());
    service.control(quiz.id(), new ControlRequest("QUESTION_OPEN", first.id(), 30));
    assertThrows(DomainException.class, () -> service.activateQuestionSet(quiz.id(), next.id()));
    assertEquals(set.id(), service.activity(quiz.id()).activeQuestionSetId());
    service.control(quiz.id(), new ControlRequest("ANSWER_REVEALED", first.id(), 0));
    service.activateQuestionSet(quiz.id(), next.id());
    assertEquals("LOBBY", service.controlState(quiz.id()).stage());
    assertNull(service.controlState(quiz.id()).questionId());
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
