package com.matrixlive.realtime;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;

@Configuration
@ConditionalOnProperty(prefix = "app.realtime", name = "redis-enabled", havingValue = "true")
public class RedisRealtimeConfiguration {
  @Bean
  RedisMessageListenerContainer realtimeRedisListener(RedisConnectionFactory connectionFactory,
      RealtimeEventBus events, RealtimeProperties properties) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener((message, pattern) -> events.receive(new String(message.getBody(), StandardCharsets.UTF_8)),
        new ChannelTopic(properties.getChannel()));
    return container;
  }
}
