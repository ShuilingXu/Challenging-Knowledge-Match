package com.matrixlive.security.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.matrixlive.service.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest(
    properties = {
      "APP_BOOTSTRAP_PASSWORD=ChangeMe!2026",
      "app.security.jwt.secret=VGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2UtaW4tcHJvZHVjdGlvbiE="
    })
class HumanVerificationTest {
  @Autowired HumanVerificationService verification;
  @Autowired HumanChallengeRepository challenges;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired AuthRateLimiter limiter;

  @Test
  void wrongAnswerConsumesTheChallengeAndExpiredChallengesCannotBeUsed() {
    UUID id = TestChallenges.create(challenges);
    String contact = UUID.randomUUID().toString();
    assertThrows(
        DomainException.class, () -> verification.verify(id, "WRONG", "127.0.0.1", contact));
    assertThrows(
        DomainException.class, () -> verification.verify(id, "ABCDE", "127.0.0.1", contact));
    UUID expired = TestChallenges.create(challenges);
    jdbc.update(
        "update human_challenges set expires_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        expired);
    assertThrows(
        DomainException.class, () -> verification.verify(expired, "ABCDE", "127.0.0.1", contact));
  }

  @Test
  void limitsRequestsAcrossTransactionsAndDifferentChallengeIds() {
    String bucket = UUID.randomUUID().toString();
    for (int i = 0; i < 3; i++) limiter.check("test-rate", bucket, 3);
    DomainException exception =
        assertThrows(DomainException.class, () -> limiter.check("test-rate", bucket, 3));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
  }
}
