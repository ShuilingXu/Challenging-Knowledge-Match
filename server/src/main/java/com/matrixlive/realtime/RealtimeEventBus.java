package com.matrixlive.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bridges local STOMP brokers through Redis so WebSocket clients can be connected to any API node. */
@Service
public class RealtimeEventBus {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RealtimeEventBus.class);
  private final SimpMessagingTemplate messaging;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final RealtimeProperties properties;

  public RealtimeEventBus(SimpMessagingTemplate messaging, StringRedisTemplate redis, ObjectMapper mapper,
      RealtimeProperties properties) {
    this.messaging = messaging;
    this.redis = redis;
    this.mapper = mapper;
    this.properties = properties;
  }

  public void send(String destination, Object payload) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { safePublish(destination, payload); }
      });
    } else {
      safePublish(destination, payload);
    }
  }

  private void safePublish(String destination, Object payload) {
    try { publish(destination, payload); }
    catch (Exception exception) { LOG.error("Real-time delivery failed after commit: {}", destination, exception); }
  }

  private void publish(String destination, Object payload) {
    if (!properties.isRedisEnabled()) {
      messaging.convertAndSend(destination, payload);
      return;
    }
    try {
      redis.convertAndSend(properties.getChannel(), mapper.writeValueAsString(new Envelope(destination, payload, Instant.now())));
    } catch (Exception exception) {
      LOG.warn("Redis unavailable; using local STOMP delivery", exception);
      messaging.convertAndSend(destination, payload);
    }
  }

  public void receive(String value) {
    try {
      Envelope event = mapper.readValue(value, Envelope.class);
      messaging.convertAndSend(event.destination(), event.payload());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid Redis real-time event", exception);
    }
  }

  public record Envelope(String destination, Object payload, Instant sentAt) { }
}
