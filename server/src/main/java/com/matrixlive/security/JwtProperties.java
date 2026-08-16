package com.matrixlive.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
  private String secret;
  private String issuer;
  private Duration accessTokenTtl = Duration.ofMinutes(15);
  private Duration participantTokenTtl = Duration.ofHours(2);
  private Duration refreshTokenTtl = Duration.ofDays(30);
  private boolean refreshCookieSecure;

  public String getSecret() { return secret; }
  public void setSecret(String secret) { this.secret = secret; }
  public String getIssuer() { return issuer; }
  public void setIssuer(String issuer) { this.issuer = issuer; }
  public Duration getAccessTokenTtl() { return accessTokenTtl; }
  public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
  public Duration getParticipantTokenTtl() { return participantTokenTtl; }
  public void setParticipantTokenTtl(Duration participantTokenTtl) { this.participantTokenTtl = participantTokenTtl; }
  public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
  public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
  public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
  public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }
}
