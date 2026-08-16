package com.matrixlive.security.auth;

import static com.matrixlive.security.auth.AuthModels.*;

import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.service.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAdminService {
  private final UserAccountRepository users;
  private final ActivityMembershipRepository memberships;
  private final ActivityRepository activities;
  private final PasswordEncoder passwordEncoder;

  public IdentityAdminService(UserAccountRepository users, ActivityMembershipRepository memberships,
      ActivityRepository activities, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.memberships = memberships;
    this.activities = activities;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
    if (users.findByUsernameIgnoreCase(username).isPresent()) {
      throw new DomainException(HttpStatus.CONFLICT, "Username is already in use");
    }
    if (request.systemRole() != null && request.systemRole() != UserRole.SYSTEM_ADMIN) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "Only SYSTEM_ADMIN can be assigned as a global role");
    }
    return toUser(users.save(new UserAccount(username, request.displayName().trim(),
        passwordEncoder.encode(request.password()), request.systemRole())));
  }

  public List<UserResponse> listUsers() { return users.findAll().stream().map(this::toUser).toList(); }

  @Transactional
  public UserResponse setEnabled(UUID userId, boolean enabled) {
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "User does not exist"));
    user.setEnabled(enabled);
    return toUser(user);
  }

  public List<MembershipResponse> listMemberships(UUID activityId) {
    requireActivity(activityId);
    return memberships.findByActivityIdOrderByCreatedAtAsc(activityId).stream().map(this::toMembership).toList();
  }

  @Transactional
  public MembershipResponse upsertMembership(UUID activityId, MembershipRequest request) {
    requireActivity(activityId);
    users.findById(request.userId()).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "User does not exist"));
    if (request.role() != UserRole.ACTIVITY_ADMIN && request.role() != UserRole.STAFF) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "Membership role must be ACTIVITY_ADMIN or STAFF");
    }
    ActivityMembership membership = memberships.findByUserIdAndActivityId(request.userId(), activityId)
        .map(existing -> { existing.changeRole(request.role()); return existing; })
        .orElseGet(() -> new ActivityMembership(activityId, request.userId(), request.role()));
    return toMembership(memberships.save(membership));
  }

  @Transactional
  public MembershipResponse createActivityMember(UUID activityId, CreateActivityMemberRequest request) {
    requireActivity(activityId);
    String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
    if (users.findByUsernameIgnoreCase(username).isPresent()) {
      throw new DomainException(HttpStatus.CONFLICT, "Username is already in use; add the existing account instead");
    }
    if (request.role() != UserRole.ACTIVITY_ADMIN && request.role() != UserRole.STAFF) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "Membership role must be ACTIVITY_ADMIN or STAFF");
    }
    UserAccount account = users.save(new UserAccount(username, request.displayName().trim(),
        passwordEncoder.encode(request.password()), null));
    return toMembership(memberships.save(new ActivityMembership(activityId, account.getId(), request.role())));
  }

  @Transactional
  public void removeMembership(UUID activityId, UUID userId) {
    requireActivity(activityId);
    memberships.deleteByUserIdAndActivityId(userId, activityId);
  }

  private void requireActivity(UUID activityId) {
    if (!activities.existsById(activityId)) throw new DomainException(HttpStatus.NOT_FOUND, "Activity does not exist");
  }

  private UserResponse toUser(UserAccount user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
        user.getSystemRole() == null ? null : user.getSystemRole().name(), user.isEnabled(), user.getCreatedAt());
  }

  private MembershipResponse toMembership(ActivityMembership membership) {
    UserAccount user = users.findById(membership.getUserId()).orElse(null);
    return new MembershipResponse(membership.getId(), membership.getActivityId(), membership.getUserId(),
        user == null ? null : user.getUsername(), user == null ? null : user.getDisplayName(),
        membership.getRole().name(), membership.getCreatedAt());
  }
}
