package com.team04.mopl.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team04.mopl.common.redis.RedisMessageSubscriber;

// Redis Pub/Sub 설정 (mopl.redis.pubsub.enabled=false 로 비활성화 가능)
@Configuration
@ConditionalOnProperty(name = "mopl.redis.pubsub.enabled", havingValue = "true", matchIfMissing = true)
public class RedisMessageConfig {

	public static final String STOMP_BROADCAST_CHANNEL = "stomp:broadcast";

	@Bean
	public RedisTemplate<String, Object> redisMessageTemplate(RedisConnectionFactory connectionFactory) {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule()); // Instant 등 Java 8 날짜 지원

		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer()); // 채널명 문자열
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper)); // 메시지는 Json
		return template;
	}

	// 채널 구독 컨테이너
	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
		RedisConnectionFactory connectionFactory,
		MessageListenerAdapter stompBroadcastListenerAdapter
	) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(stompBroadcastListenerAdapter, new PatternTopic(STOMP_BROADCAST_CHANNEL));
		return container;
	}

	// 구독 리스너 컨테이너
	@Bean
	public MessageListenerAdapter stompBroadcastListenerAdapter(RedisMessageSubscriber subscriber) {
		return new MessageListenerAdapter(subscriber, "onMessage");
	}
}
