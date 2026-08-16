package com.matrixlive.security;

import com.matrixlive.security.JwtTokenService.TokenClaims;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
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

  public StompJwtChannelInterceptor(JwtTokenService tokens, TokenRevocationService revocations,
      ActivityMembershipRepository memberships, UserAccountRepository users) {
    this.tokens = tokens;
    this.revocations = revocations;
    this.memberships = memberships;
    this.users = users;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (headers == null) return message;
    if (StompCommand.CONNECT.equals(headers.getCommand())) {
      headers.setUser(authenticate(headers));
      return message;
    }
    if (StompCommand.SUBSCRIBE.equals(headers.getCommand())) {
      if (!(headers.getUser() instanceof Authentication authentication)
          || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)
          || !isAllowedSubscription(principal, headers.getDestination())) {
        throw new AccessDeniedException("Not authorized to subscribe to this topic");
      }
    }
    return message;
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
        && principal.deviceId().toString().equals(device.group(1));
    Matcher activity = ACTIVITY_TOPIC.matcher(destination);
    if (!activity.matches()) return false;
    UUID activityId = UUID.fromString(activity.group(1));
    if (principal.isSystemAdmin()) return true;
    if (principal.isParticipant()) return activityId.equals(principal.activityId());
    return principal.userId() != null && memberships.findByUserIdAndActivityId(principal.userId(), activityId).isPresent();
  }
}
