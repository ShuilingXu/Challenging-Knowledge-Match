package com.matrixlive.security.auth;

import java.time.Instant;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
public class TokenCleanup {
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final RefreshTokenRepository refresh;
  private final RevokedAccessTokenRepository revoked;
  private final ClientCredentialRepository clients;
  private final HumanChallengeRepository challenges;

  public TokenCleanup(
      RefreshTokenRepository refresh,
      RevokedAccessTokenRepository revoked,
      ClientCredentialRepository clients,
      HumanChallengeRepository challenges,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.refresh = refresh;
    this.revoked = revoked;
    this.clients = clients;
    this.challenges = challenges;
  }

  @Scheduled(fixedDelay = 3600000)
  @Transactional
  public void clean() {
    Instant now = Instant.now();
    refresh.deleteByExpiresAtBefore(now);
    revoked.deleteByExpiresAtBefore(now);
    clients.deleteByExpiresAtBefore(now);
    challenges.deleteByCreatedAtBefore(now.minusSeconds(600));
    jdbc.update("delete from auth_rate_limits where expires_at < ?", java.sql.Timestamp.from(now));
    jdbc.update(
        "update refresh_tokens set replacement_ciphertext=null where revoked_at < ? and"
            + " replacement_ciphertext is not null",
        java.sql.Timestamp.from(now.minusSeconds(10)));
  }
}
