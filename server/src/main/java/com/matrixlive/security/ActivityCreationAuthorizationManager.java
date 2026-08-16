package com.matrixlive.security;

import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserRole;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Creation has no activity id yet, so it is granted to system admins and existing activity administrators only. */
@Component
public class ActivityCreationAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  private final ActivityMembershipRepository memberships;

  public ActivityCreationAuthorizationManager(ActivityMembershipRepository memberships) { this.memberships = memberships; }

  @Override
  public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    if (current == null || !(current.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
      return new AuthorizationDecision(false);
    }
    boolean allowed = principal.isSystemAdmin() || (principal.userId() != null
        && memberships.existsByUserIdAndRole(principal.userId(), UserRole.ACTIVITY_ADMIN));
    return new AuthorizationDecision(allowed);
  }
}
