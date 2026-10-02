package com.matrixlive.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.matrixlive.security.auth.*;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.screen.ScreenDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;

class StompSessionAuthorizationTest {
  private final JwtTokenService tokens = mock(JwtTokenService.class);
  private final TokenRevocationService revocations = mock(TokenRevocationService.class);
  private final ActivityMembershipRepository memberships = mock(ActivityMembershipRepository.class);
  private final UserAccountRepository users = mock(UserAccountRepository.class);
  private final UUID userId = UUID.randomUUID(), activityId = UUID.randomUUID(), tokenId = UUID.randomUUID();
  private final UserAccount account = new UserAccount("socket-test", "Socket", "hash", null);
  private StompJwtChannelInterceptor interceptor;
  private java.security.Principal connectionUser;

  @BeforeEach void initialize() {
    interceptor = new StompJwtChannelInterceptor(tokens, revocations, memberships, users,
        mock(ActivityRepository.class), mock(ScreenDeviceRepository.class));
    when(users.findById(userId)).thenReturn(Optional.of(account));
    when(memberships.findByUserIdAndActivityId(userId, activityId))
        .thenReturn(Optional.of(new ActivityMembership(activityId, userId, UserRole.STAFF)));
    connect(Instant.now().plusSeconds(60));
  }

  private void connect(Instant expiresAt) {
    when(tokens.parse("token")).thenReturn(new JwtTokenService.TokenClaims(tokenId, PrincipalKind.ACCOUNT,
        userId, null, null, null, UserRole.STAFF, "socket-test", expiresAt, null));
    var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
    headers.setSessionId("session");
    headers.setNativeHeader("Authorization", "Bearer token");
    headers.setLeaveMutable(true);
    interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    connectionUser = headers.getUser();
  }

  private Message<byte[]> delivery() {
    // The simple broker targets a session without copying the inbound simpUser.
    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setSessionId("session");
    headers.setDestination("/topic/activities/" + activityId);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }

  @Test void brokerDeliveryUsesSessionIdentityAndRechecksMembership() {
    assertNotNull(interceptor.outboundAuthorization().preSend(delivery(), null));
    when(memberships.findByUserIdAndActivityId(userId, activityId)).thenReturn(Optional.empty());
    assertNull(interceptor.outboundAuthorization().preSend(delivery(), null));
  }

  @Test void revokedTokenStopsExistingDeliveryAndNewSubscriptions() {
    when(revocations.isAccessTokenRevoked(tokenId)).thenReturn(true);
    assertNull(interceptor.outboundAuthorization().preSend(delivery(), null));
    var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    headers.setSessionId("session");
    headers.setUser(connectionUser);
    headers.setDestination("/topic/activities/" + activityId);
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
  }

  @Test void disabledAccountStopsExistingDelivery() {
    account.setEnabled(false);
    assertNull(interceptor.outboundAuthorization().preSend(delivery(), null));
  }

  @Test void expiredSessionStopsExistingDelivery() {
    connect(Instant.now().minusSeconds(1));
    assertNull(interceptor.outboundAuthorization().preSend(delivery(), null));
  }

  @Test void unknownSessionCannotReceiveActivityMessages() {
    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setSessionId("unknown");
    headers.setDestination("/topic/activities/" + activityId);
    assertNull(interceptor.outboundAuthorization().preSend(
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
  }
}
