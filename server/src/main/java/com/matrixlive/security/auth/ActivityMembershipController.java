package com.matrixlive.security.auth;

import static com.matrixlive.security.auth.AuthModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities/{activityId}/memberships")
public class ActivityMembershipController {
  private final IdentityAdminService identity;

  public ActivityMembershipController(IdentityAdminService identity) { this.identity = identity; }

  @GetMapping
  public List<MembershipResponse> list(@PathVariable UUID activityId) { return identity.listMemberships(activityId); }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MembershipResponse upsert(@PathVariable UUID activityId, @Valid @RequestBody MembershipRequest request) {
    return identity.upsertMembership(activityId, request);
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public MembershipResponse createUser(@PathVariable UUID activityId,
      @Valid @RequestBody CreateActivityMemberRequest request) {
    return identity.createActivityMember(activityId, request);
  }

  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID activityId, @PathVariable UUID userId) {
    identity.removeMembership(activityId, userId);
  }
}
