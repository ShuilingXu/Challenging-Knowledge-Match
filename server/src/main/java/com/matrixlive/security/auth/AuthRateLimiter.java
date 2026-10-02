package com.matrixlive.security.auth;

import com.matrixlive.service.DomainException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuthRateLimiter {
  private final JdbcTemplate jdbc;
  private final boolean h2;

  public AuthRateLimiter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.h2 =
        Boolean.TRUE.equals(
            jdbc.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                    c -> c.getMetaData().getDatabaseProductName().equals("H2")));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DomainException.class)
  public void check(String scope, String value, int limit) {
    long window = Instant.now().getEpochSecond() / 300;
    String key = AuthService.hash(scope + ":" + value + ":" + window);
    var until = java.sql.Timestamp.from(Instant.ofEpochSecond((window + 2) * 300));
    if (h2)
      jdbc.update(
          "merge into auth_rate_limits t using (values (?, ?)) s(rate_key, expires_at) on"
              + " t.rate_key=s.rate_key when not matched then insert (rate_key, attempts,"
              + " expires_at) values (s.rate_key,0,s.expires_at)",
          key,
          until);
    else
      jdbc.update(
          "insert into auth_rate_limits(rate_key,attempts,expires_at) values (?,0,?) on"
              + " conflict(rate_key) do nothing",
          key,
          until);
    if (jdbc.update(
            "update auth_rate_limits set attempts=attempts+1 where rate_key=? and attempts < ?",
            key,
            limit)
        != 1) throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "验证请求过于频繁，请稍后重试");
  }
}
