package com.matrixlive.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.realtime")
public class RealtimeProperties {
  private boolean redisEnabled;
  private String channel = "matrixlive.events";

  public boolean isRedisEnabled() { return redisEnabled; }
  public void setRedisEnabled(boolean redisEnabled) { this.redisEnabled = redisEnabled; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
}
