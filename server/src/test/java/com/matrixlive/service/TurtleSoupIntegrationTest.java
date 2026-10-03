package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.matrixlive.service.TurtleSoupService.*;
import com.matrixlive.screen.ScreenService;
import com.matrixlive.screen.ScreenModels.*;
import com.matrixlive.screen.ScreenDisplayMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TurtleSoupIntegrationTest {
  @Autowired ActivityService activities;
  @Autowired TurtleSoupService soup;
  @Autowired ScreenService screens;
  @Autowired MockMvc mvc;
  @Autowired com.matrixlive.security.JwtTokenService jwt;
  @Autowired com.matrixlive.security.auth.UserAccountRepository users;
  @Autowired com.matrixlive.security.auth.ActivityMembershipRepository memberships;
  @Autowired com.matrixlive.repository.ActivityRepository repository;
  private ActivityResponse create() {
    var parent=activities.createActivity(new CreateActivityRequest("Soup main", "Shanghai", Instant.now()));
    return activities.createActivity(new CreateActivityRequest("Soup round", "Shanghai", Instant.now(), null, null,
        null, null, null, null, parent.id(), "TURTLE_SOUP"));
  }
  @Test void revealsOnlySelectedContentAndPersistsStateOnBothScreens() {
    var activity=create();
    var mainScreen=screens.registerDevice(activity.parentActivityId(), new RegisterScreenDeviceRequest("Main",1920,1080));
    var childScreen=screens.registerDevice(activity.id(), new RegisterScreenDeviceRequest("Soup",1920,1080));
    var state=soup.save(activity.id(), new Content("Mystery", "Visible surface", List.of("First clue", "Secret clue"), "Secret solution"));
    long revision=state.revision();
    assertThrows(DomainException.class, () -> soup.control(activity.id(), new Command("SHOW_SURFACE",revision)));
    QuizTestSupport.start(activities, activity.id());
    state=soup.control(activity.id(), new Command("SHOW_SURFACE",revision));
    var display=screens.currentDisplay(activity.parentActivityId(),mainScreen.device().id());
    assertEquals(ScreenDisplayMode.TURTLE_SOUP,display.mode());
    assertFalse(display.data().containsKey("solution"));
    assertEquals(List.of(),display.data().get("clues"));
    state=soup.control(activity.id(), new Command("NEXT_CLUE",state.revision()));
    assertEquals(List.of("First clue"),screens.currentDisplay(activity.id(),childScreen.device().id()).data().get("clues"));
    assertEquals(state,soup.state(activity.id()));
    var lateScreen=screens.registerDevice(activity.id(),new RegisterScreenDeviceRequest("Late screen",1920,1080));
    var lateDisplay=screens.currentDisplay(activity.id(),lateScreen.device().id());
    assertEquals(List.of("First clue"),lateDisplay.data().get("clues"));
    assertFalse(lateDisplay.data().containsKey("solution"));
    assertThrows(DomainException.class, () -> soup.control(activity.id(),new Command("NEXT_CLUE",revision)));
    assertThrows(DomainException.class, () -> soup.save(activity.id(),new Content("Changed","surface",List.of(),"solution")));
    state=soup.control(activity.id(),new Command("PREVIOUS_CLUE",state.revision()));
    assertEquals(List.of(),screens.currentDisplay(activity.id(),childScreen.device().id()).data().get("clues"));
    state=soup.control(activity.id(),new Command("REVEAL_SOLUTION",state.revision()));
    assertEquals("Secret solution",screens.currentDisplay(activity.parentActivityId(),mainScreen.device().id()).data().get("solution"));
    soup.control(activity.id(),new Command("RESET",state.revision()));
    assertFalse(screens.currentDisplay(activity.id(),childScreen.device().id()).data().containsKey("surface"));
  }
  @Test void pauseClearsTheDisplayAndInvalidatesStaleCommands() {
    var activity=create();
    var state=soup.save(activity.id(),new Content("Mystery","Surface",List.of(),"Solution"));
    QuizTestSupport.start(activities,activity.id());
    state=soup.control(activity.id(),new Command("SHOW_SURFACE",state.revision()));
    activities.changeActivityStatus(activity.parentActivityId(),new ChangeActivityStatusRequest("PAUSED"));
    assertEquals("LOBBY",soup.state(activity.id()).stage());
    assertTrue(soup.state(activity.id()).revision()>state.revision());
    assertThrows(DomainException.class, () -> activities.control(activity.id(),new ControlRequest("QUESTION_OPEN",null,0)));
    assertThrows(DomainException.class, () -> activities.register(activity.id(),"main",new RegisterParticipantRequest("Viewer","contact",null)));
  }
  @Test void anonymousRequestsCannotReadThePrivateScript() throws Exception {
    var activity=create();
    mvc.perform(get("/api/activities/"+activity.id()+"/turtle-soup")).andExpect(status().isUnauthorized());
  }
  @Test void freelySelectsCluesInPublicOrderAndAddsPrivateOrImmediateHostClues() {
    var activity=create();
    var device=screens.registerDevice(activity.id(),new RegisterScreenDeviceRequest("Interactive",1920,1080));
    var state=soup.save(activity.id(),new Content("Mystery","Surface",List.of("First","Second","Third"),"Secret solution"));
    QuizTestSupport.start(activities,activity.id());
    state=soup.control(activity.id(),new Command("SHOW_SURFACE",state.revision()));
    state=soup.control(activity.id(),new Command("SHOW_CLUE",state.revision(),2,null,null));
    state=soup.control(activity.id(),new Command("SHOW_CLUE",state.revision(),0,null,null));
    var display=screens.currentDisplay(activity.id(),device.device().id());
    assertEquals(List.of("Third","First"),display.data().get("clues"));
    assertEquals(List.of(3,1),display.data().get("clueNumbers"));
    assertFalse(display.data().toString().contains("Second"));
    assertFalse(display.data().containsKey("solution"));
    state=soup.control(activity.id(),new Command("HIDE_CLUE",state.revision(),2,null,null));
    assertEquals(List.of(0),state.revealedClueIndexes());
    state=soup.control(activity.id(),new Command("ADD_CLUE",state.revision(),null,"Private improvisation",false));
    assertEquals(4,state.content().clues().size());
    assertEquals(List.of("First"),screens.currentDisplay(activity.id(),device.device().id()).data().get("clues"));
    state=soup.control(activity.id(),new Command("ADD_CLUE",state.revision(),null,"Immediate improvisation",true));
    assertEquals(List.of("First","Immediate improvisation"),screens.currentDisplay(activity.id(),device.device().id()).data().get("clues"));
    assertEquals(state,soup.state(activity.id()));
    long revision=state.revision();
    assertThrows(DomainException.class,() -> soup.control(activity.id(),new Command("SHOW_CLUE",revision,99,null,null)));
    assertThrows(DomainException.class,() -> soup.control(activity.id(),new Command("ADD_CLUE",revision,null,"  ",false)));
    var reset=soup.control(activity.id(),new Command("RESET",revision));
    assertTrue(reset.revealedClueIndexes().isEmpty());
    assertEquals(5,reset.content().clues().size());
  }
  @Test void restoresPreviouslySavedConsecutiveStates() {
    var activity=create();
    repository.findById(activity.id()).orElseThrow().updateTurtleSoup("{\"content\":{\"title\":\"Old\",\"surface\":\"Surface\",\"clues\":[\"A\",\"B\",\"C\"],\"solution\":\"Secret\"},\"stage\":\"SURFACE\",\"revealedClues\":2,\"revision\":7}");
    var state=soup.state(activity.id());
    assertEquals(List.of(0,1),state.revealedClueIndexes());
    assertEquals(List.of("A","B"),TurtleSoupService.display(state).get("clues"));
    assertEquals(7,state.revision());
  }
  @Test void staffMayControlButOnlyAdminsMayEditAndDevicesCannotReadThePrivateScript() throws Exception {
    var activity=create();
    var state=soup.save(activity.id(),new Content("Mystery","Surface",List.of(),"Secret"));
    QuizTestSupport.start(activities,activity.id());
    var staff=users.findByUsernameIgnoreCase("event-staff").orElseThrow();
    memberships.save(new com.matrixlive.security.auth.ActivityMembership(activity.parentActivityId(),staff.getId(),com.matrixlive.security.auth.UserRole.STAFF));
    String token=jwt.issueAccountToken(staff).value();
    String route="/api/activities/"+activity.id()+"/turtle-soup";
    mvc.perform(get(route).header("Authorization","Bearer "+token)).andExpect(status().isOk());
    mvc.perform(put(route).header("Authorization","Bearer "+token).contentType("application/json")
        .content("{\"title\":\"Edit\",\"surface\":\"Surface\",\"clues\":[],\"solution\":\"Secret\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post(route+"/control").header("Authorization","Bearer "+token).contentType("application/json")
        .content("{\"action\":\"SHOW_SURFACE\",\"revision\":"+state.revision()+"}"))
        .andExpect(status().isOk());
    mvc.perform(post(route+"/control").header("Authorization","Bearer "+token).contentType("application/json")
        .content("{\"action\":\"ADD_CLUE\",\"revision\":"+(state.revision()+1)+",\"clue\":\"Host addition\",\"publishNow\":true}"))
        .andExpect(status().isOk());
    String participantToken=jwt.issueParticipantToken(activity.id(),java.util.UUID.randomUUID()).value();
    mvc.perform(get(route).header("Authorization","Bearer "+participantToken)).andExpect(status().isForbidden());
    var device=screens.registerDevice(activity.id(),new RegisterScreenDeviceRequest("Device",1920,1080));
    String screenToken=jwt.issueScreenDeviceToken(activity.id(),device.device().id()).value();
    mvc.perform(get(route).header("Authorization","Bearer "+screenToken)).andExpect(status().isForbidden());
  }
}

