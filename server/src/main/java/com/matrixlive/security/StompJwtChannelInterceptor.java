package com.matrixlive.security;

import com.matrixlive.security.JwtTokenService.TokenClaims;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.screen.ScreenDeviceRepository;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.SimpMessageType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** Secures STOMP CONNECT and subscriptions because a browser WebSocket handshake cannot carry HTTP Bearer headers. */
@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {
  private static final Pattern ACTIVITY_TOPIC = Pattern.compile("^/topic/activities/([0-9a-fA-F-]{36})(?:/screens)?$");
  private static final Pattern DEVICE_TOPIC = Pattern.compile("^/topic/screens/([0-9a-fA-F-]{36})$");
  private final JwtTokenService tokens;
  private final TokenRevocationService revocations;
  private final ActivityMembershipRepository memberships;
  private final UserAccountRepository users;
  private final ActivityRepository activities;
  private final ScreenDeviceRepository devices;
  private final ConcurrentHashMap<String, AuthenticatedPrincipal> sessions = new ConcurrentHashMap<>();

  public StompJwtChannelInterceptor(JwtTokenService tokens, TokenRevocationService revocations,
      ActivityMembershipRepository memberships, UserAccountRepository users, ActivityRepository activities,
      ScreenDeviceRepository devices) {
    this.tokens = tokens;
    this.revocations = revocations;
    this.memberships = memberships;
    this.users = users;
    this.activities = activities;
    this.devices = devices;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (headers == null) return message;
    if (StompCommand.SEND.equals(headers.getCommand())) {
      throw new AccessDeniedException("Clients cannot publish server events");
    }
    if (StompCommand.CONNECT.equals(headers.getCommand())) {
      Authentication authentication = authenticate(headers);
      headers.setUser(authentication);
      if (headers.getSessionId() != null) {
        sessions.put(headers.getSessionId(), (AuthenticatedPrincipal) authentication.getPrincipal());
      }
      return message;
    }
    if (StompCommand.SUBSCRIBE.equals(headers.getCommand())) {
      if (!(headers.getUser() instanceof Authentication authentication)
          || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)
          || !isCurrent(principal)
          || !isAllowedSubscription(principal, headers.getDestination())) {
        throw new AccessDeniedException("Not authorized to subscribe to this topic");
      }
    }
    return message;
  }

  /** The broker supplies a session id on each delivery, but does not retain simpUser. */
  public ChannelInterceptor outboundAuthorization() {
    return new ChannelInterceptor() {
      @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
        AuthenticatedPrincipal principal = headers.getSessionId() == null ? null : sessions.get(headers.getSessionId());
        return principal != null && isCurrent(principal)
            && isAllowedSubscription(principal, headers.getDestination()) ? message : null;
      }
    };
  }

  @EventListener
  public void disconnected(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }

  private boolean isCurrent(AuthenticatedPrincipal principal) {
    if (principal.expiresAt() == null || !principal.expiresAt().isAfter(Instant.now())
        || revocations.isAccessTokenRevoked(principal.tokenId())) return false;
    if (principal.kind() == PrincipalKind.ACCOUNT) {
      return principal.userId() != null && users.findById(principal.userId()).filter(UserAccount::isEnabled)
          .map(account -> account.getSystemRole() == principal.role()
              || (account.getSystemRole() == null && principal.role() == UserRole.STAFF)).orElse(false);
    }
    return !principal.isScreenDevice() || (principal.deviceId() != null && principal.activityId() != null
        && devices.findByIdAndActivityId(principal.deviceId(), principal.activityId()).isPresent());
  }

  private Authentication authenticate(StompHeaderAccessor headers) {
    String value = headers.getFirstNativeHeader("Authorization");
    if (value == null) value = headers.getFirstNativeHeader("authorization");
    if (value == null || !value.startsWith("Bearer ")) throw new AccessDeniedException("STOMP access token is required");
    try {
      TokenClaims claims = tokens.parse(value.substring(7));
      if (revocations.isAccessTokenRevoked(claims.tokenId())) throw new AccessDeniedException("Access token is revoked");
      if (claims.kind() == PrincipalKind.ACCOUNT && !isEnabledAccount(claims)) {
        throw new AccessDeniedException("Account is unavailable");
      }
      if (claims.kind() == PrincipalKind.SCREEN_DEVICE && (claims.deviceId() == null || claims.activityId() == null
          || devices.findByIdAndActivityId(claims.deviceId(), claims.activityId()).isEmpty())) {
        throw new AccessDeniedException("Screen device is unavailable");
      }
      AuthenticatedPrincipal principal = new AuthenticatedPrincipal(claims.tokenId(), claims.kind(), claims.userId(),
          claims.participantId(), claims.deviceId(), claims.activityId(), claims.role(), claims.username(), claims.expiresAt());
      return new UsernamePasswordAuthenticationToken(principal, null,
          List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
    } catch (JwtException | IllegalArgumentException exception) {
      throw new AccessDeniedException("Invalid STOMP access token", exception);
    }
  }

  private boolean isEnabledAccount(TokenClaims claims) {
    return claims.userId() != null && users.findById(claims.userId()).filter(UserAccount::isEnabled)
        .map(account -> account.getSystemRole() == claims.role()
            || (account.getSystemRole() == null && claims.role() == UserRole.STAFF))
        .orElse(false);
  }

  private boolean isAllowedSubscription(AuthenticatedPrincipal principal, String destination) {
    if (destination == null) return false;
    Matcher device = DEVICE_TOPIC.matcher(destination);
    if (device.matches()) return principal.isScreenDevice() && principal.deviceId() != null
        && principal.deviceId().toString().equals(device.group(1))
        && devices.findByIdAndActivityId(principal.deviceId(), principal.activityId()).isPresent();
    Matcher activity = ACTIVITY_TOPIC.matcher(destination);
    if (!activity.matches()) return false;
    UUID activityId = UUID.fromString(activity.group(1));
    if (principal.isSystemAdmin()) return true;
    if (principal.isParticipant()) {
      boolean permittedActivity = activityId.equals(principal.activityId()) || activities.findById(activityId)
          .filter(item -> "LOTTERY".equals(item.getActivityType()) && principal.activityId().equals(item.getParentActivityId()))
          .isPresent();
      return permittedActivity && !destination.endsWith("/screens");
    }
    if (principal.userId() == null) return false;
    if (memberships.findByUserIdAndActivityId(principal.userId(), activityId).isPresent()) return true;
    return activities.findById(activityId).map(item -> item.getParentActivityId())
        .flatMap(parentId -> memberships.findByUserIdAndActivityId(principal.userId(), parentId)).isPresent();
  }
}
