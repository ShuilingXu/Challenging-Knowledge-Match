package com.matrixlive.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RealtimeEventBusTest {
  @Test
  void redisFailureAfterCommitFallsBackLocallyWithoutFailingTheCommittedRequest() {
    var redis = mock(StringRedisTemplate.class);
    var broker = mock(SimpMessagingTemplate.class);
    var properties = new RealtimeProperties();
    properties.setRedisEnabled(true);
    properties.setChannel("test.events");
    var bus =
        new RealtimeEventBus(
            broker, redis, new ObjectMapper().findAndRegisterModules(), properties);
    when(redis.convertAndSend(anyString(), anyString()))
        .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
    var payload = Map.of("type", "score.adjusted");
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      bus.send("/topic/activities/test", payload);
      verifyNoInteractions(broker);
      assertDoesNotThrow(
          () ->
              TransactionSynchronizationManager.getSynchronizations()
                  .forEach(sync -> sync.afterCommit()));
      verify(broker).convertAndSend("/topic/activities/test", payload);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
