package com.matrixlive.security;

import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Centralizes activity-scoped policy so controllers do not make authorization decisions. */
@Component
public class ActivityAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  private static final Pattern ACTIVITY_PATH = Pattern.compile("^/api/activities/([0-9a-fA-F-]{36})(?:/(.*))?$");
  private final ActivityMembershipRepository memberships;

  public ActivityAuthorizationManager(ActivityMembershipRepository memberships) { this.memberships = memberships; }

  @Override
  public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    Matcher matcher = ACTIVITY_PATH.matcher(request.getRequestURI());
    if (!matcher.matches()) return new AuthorizationDecision(false);
    UUID activityId;
    try {
      activityId = UUID.fromString(matcher.group(1));
    } catch (IllegalArgumentException exception) {
      return new AuthorizationDecision(false);
    }
    String remainder = matcher.group(2) == null ? "" : matcher.group(2);
    Authentication current = authentication.get();
    if (current == null || !current.isAuthenticated() || !(current.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
      return new AuthorizationDecision(false);
    }
    if (principal.isSystemAdmin()) return new AuthorizationDecision(true);
    if (principal.isParticipant()) return new AuthorizationDecision(allowsParticipant(request, remainder, activityId, principal));
    if (principal.isScreenDevice()) return new AuthorizationDecision(allowsScreenDevice(request, remainder, activityId, principal));
    if (principal.userId() == null) return new AuthorizationDecision(false);
    UserRole membershipRole = memberships.findByUserIdAndActivityId(principal.userId(), activityId)
        .map(item -> item.getRole()).orElse(null);
    if (membershipRole == null) return new AuthorizationDecision(false);
    if (membershipRole == UserRole.ACTIVITY_ADMIN) return new AuthorizationDecision(true);
    return new AuthorizationDecision(allowsStaff(request, remainder));
  }

  private boolean allowsParticipant(HttpServletRequest request, String remainder, UUID activityId,
      AuthenticatedPrincipal principal) {
    if (!activityId.equals(principal.activityId())) return false;
    String method = request.getMethod();
    if ("questions".equals(remainder) || "scoreboard".equals(remainder) || "control".equals(remainder)) {
      return "GET".equals(method);
    }
    if ("answers".equals(remainder) || "draws".equals(remainder)) return "POST".equals(method);
    if ("awards".equals(remainder) && "GET".equals(method)) {
      String participantId = request.getParameter("participantId");
      return principal.participantId().toString().equals(participantId);
    }
    if ("GET".equals(method) && remainder.startsWith("participants/")) {
      String[] segments = remainder.split("/");
      if (segments.length < 2 || !principal.participantId().toString().equals(segments[1])) return false;
      return segments.length == 2 || (segments.length == 3
          && ("submissions".equals(segments[2]) || "score-ledger".equals(segments[2]) || "lottery-chances".equals(segments[2])));
    }
    return false;
  }

  private boolean allowsScreenDevice(HttpServletRequest request, String remainder, UUID activityId,
      AuthenticatedPrincipal principal) {
    if (!activityId.equals(principal.activityId()) || !remainder.startsWith("screens/devices/")) return false;
    String[] segments = remainder.split("/");
    if (segments.length < 4 || principal.deviceId() == null) return false;
    if (!principal.deviceId().toString().equals(segments[2])) return false;
    return ("GET".equals(request.getMethod()) && remainder.endsWith("state"))
        || ("POST".equals(request.getMethod()) && remainder.endsWith("heartbeat"));
  }

  private boolean allowsStaff(HttpServletRequest request, String remainder) {
    String method = request.getMethod();
    // Viewing operational data is sufficient for the floor team, except answer keys exposed by the admin question route.
    if ("GET".equals(method)) {
      return !"questions/admin".equals(remainder)
          && !remainder.startsWith("memberships")
          && !remainder.startsWith("question-sets");
    }
    if ("control".equals(remainder)) return "POST".equals(method);
    if (remainder.matches("awards/[0-9a-fA-F-]{36}/redeem")) return "POST".equals(method);
    // Floor staff may correct participant details, but cannot change registration schema or event configuration.
    return "PATCH".equals(method) && remainder.matches("participants/[0-9a-fA-F-]{36}");
  }
}
