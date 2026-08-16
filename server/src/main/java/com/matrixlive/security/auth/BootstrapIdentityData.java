package com.matrixlive.security.auth;

import com.matrixlive.repository.ActivityRepository;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds only a fresh development database. Production deployments must replace these credentials at bootstrap. */
@Component
public class BootstrapIdentityData {
  private final UserAccountRepository users;
  private final ActivityMembershipRepository memberships;
  private final ActivityRepository activities;
  private final PasswordEncoder passwordEncoder;
  private final String bootstrapPassword;

  public BootstrapIdentityData(UserAccountRepository users, ActivityMembershipRepository memberships,
      ActivityRepository activities, PasswordEncoder passwordEncoder,
      @Value("${APP_BOOTSTRAP_PASSWORD:ChangeMe!2026}") String bootstrapPassword) {
    this.users = users;
    this.memberships = memberships;
    this.activities = activities;
    this.passwordEncoder = passwordEncoder;
    this.bootstrapPassword = bootstrapPassword;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seed() {
    UserAccount systemAdmin = ensureUser("sysadmin", "System Administrator", UserRole.SYSTEM_ADMIN);
    UserAccount activityAdmin = ensureUser("activity-admin", "Activity Administrator", null);
    UserAccount staff = ensureUser("event-staff", "Event Staff", null);
    activities.findAll().stream().min(Comparator.comparing(activity -> activity.getStartsAt() == null
        ? java.time.Instant.EPOCH : activity.getStartsAt())).ifPresent(activity -> {
          ensureMembership(activity.getId(), activityAdmin.getId(), UserRole.ACTIVITY_ADMIN);
          ensureMembership(activity.getId(), staff.getId(), UserRole.STAFF);
        });
  }

  private UserAccount ensureUser(String username, String displayName, UserRole systemRole) {
    return users.findByUsernameIgnoreCase(username)
        .orElseGet(() -> users.save(new UserAccount(username, displayName, passwordEncoder.encode(bootstrapPassword), systemRole)));
  }

  private void ensureMembership(java.util.UUID activityId, java.util.UUID userId, UserRole role) {
    memberships.findByUserIdAndActivityId(userId, activityId)
        .orElseGet(() -> memberships.save(new ActivityMembership(activityId, userId, role)));
  }
}
