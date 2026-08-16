package com.matrixlive.security;

import com.matrixlive.api.ApiModels.ActivityResponse;
import com.matrixlive.security.auth.ActivityMembership;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserRole;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Grants an activity-admin creator control of the activity that was just created. */
@Aspect
@Component
public class ActivityCreatorMembershipGuard {
  private final ActivityMembershipRepository memberships;

  public ActivityCreatorMembershipGuard(ActivityMembershipRepository memberships) { this.memberships = memberships; }

  @AfterReturning(pointcut = "execution(* com.matrixlive.service.ActivityService.createActivity(..))", returning = "activity")
  @Transactional
  public void grantCreatorMembership(ActivityResponse activity) {
    Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
        : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof AuthenticatedPrincipal current && current.userId() != null && !current.isSystemAdmin()) {
      memberships.findByUserIdAndActivityId(current.userId(), activity.id())
          .orElseGet(() -> memberships.save(new ActivityMembership(activity.id(), current.userId(), UserRole.ACTIVITY_ADMIN)));
    }
  }
}
