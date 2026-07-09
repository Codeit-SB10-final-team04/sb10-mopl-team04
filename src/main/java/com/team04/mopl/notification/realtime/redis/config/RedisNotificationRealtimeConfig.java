package com.team04.mopl.notification.realtime.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.team04.mopl.notification.realtime.redis.RedisNotificationRealtimeChannels;
import com.team04.mopl.notification.realtime.redis.RedisNotificationRealtimeSubscriber;

import lombok.RequiredArgsConstructor;

// Redis Pub/Sub 알림 채널을 구독하기 위한 Listener Container 설정
@Configuration
@ConditionalOnProperty(
	name = "notification.realtime.mode",
	havingValue = "redis"
)
@RequiredArgsConstructor
public class RedisNotificationRealtimeConfig {

	// Redis 연결 팩토리로, Listener Container가 Redis 서버에 연결할 때 사용
	private final RedisConnectionFactory redisConnectionFactory;

	// Redis 채널에서 수신한 알림 메시지를 처리하는 Subscriber
	private final RedisNotificationRealtimeSubscriber subscriber;

	// Redis 채널과 Subscriber를 연결하는 Listener Container를 생성
	// 애플리케이션이 Redis 채널을 실제로 구독하게 하는 설정
	@Bean
	public RedisMessageListenerContainer notificationRedisMessageListenerContainer() {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener(
			subscriber,
			new ChannelTopic(RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME)
		);

		return container;
	}
}
