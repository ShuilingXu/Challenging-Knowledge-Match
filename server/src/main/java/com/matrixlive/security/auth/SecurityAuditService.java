package com.matrixlive.security.auth;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {
  private final AuditEventRepository events;

  public SecurityAuditService(AuditEventRepository events) { this.events = events; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String eventType, String actorType, UUID actorId, UUID activityId, boolean success,
      String ipAddress, String userAgent, String details) {
    events.save(new AuditEvent(eventType, actorType, actorId, activityId, success, ipAddress, userAgent, details));
  }
}
