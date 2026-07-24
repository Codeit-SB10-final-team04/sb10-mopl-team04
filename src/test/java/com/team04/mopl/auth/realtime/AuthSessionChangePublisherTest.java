package com.team04.mopl.auth.realtime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthSessionChangePublisherTest {

	@Test
	@DisplayName("인증 세션 변경을 로컬과 Redis에 모두 전파한다")
	void publish_sendsLocalEventAndRedisMessage_whenRedisPubSubIsEnabled() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuthSessionChangePublisher publisher = new AuthSessionChangePublisher(
			applicationEventPublisher,
			redisTemplate,
			true
		);
		UUID userId = UUID.randomUUID();
		AuthSessionChangedEvent event = new AuthSessionChangedEvent(userId);

		// when
		publisher.publish(userId);

		// then
		verify(applicationEventPublisher).publishEvent(event);
		verify(redisTemplate).convertAndSend(AuthSessionChangePublisher.CHANNEL, userId.toString());
	}

	@Test
	@DisplayName("Redis Pub/Sub이 비활성화되면 로컬 이벤트만 발행한다")
	void publish_sendsOnlyLocalEvent_whenRedisPubSubIsDisabled() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuthSessionChangePublisher publisher = new AuthSessionChangePublisher(
			applicationEventPublisher,
			redisTemplate,
			false
		);
		UUID userId = UUID.randomUUID();

		// when
		publisher.publish(userId);

		// then
		verify(applicationEventPublisher).publishEvent(new AuthSessionChangedEvent(userId));
		verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
	}

	@Test
	@DisplayName("Redis 전파가 실패해도 인증 세션 변경 처리를 실패시키지 않는다")
	void publish_doesNotThrow_whenRedisPublishFails() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuthSessionChangePublisher publisher = new AuthSessionChangePublisher(
			applicationEventPublisher,
			redisTemplate,
			true
		);
		UUID userId = UUID.randomUUID();
		doThrow(new IllegalStateException("redis publish failed"))
			.when(redisTemplate)
			.convertAndSend(AuthSessionChangePublisher.CHANNEL, userId.toString());

		// when
		publisher.publish(userId);

		// then
		verify(applicationEventPublisher).publishEvent(new AuthSessionChangedEvent(userId));
	}

	@Test
	@DisplayName("로컬 연결 정리가 실패해도 다른 인스턴스에 인증 세션 변경을 전파한다")
	void publish_sendsRedisMessage_whenLocalListenerFails() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuthSessionChangePublisher publisher = new AuthSessionChangePublisher(
			applicationEventPublisher,
			redisTemplate,
			true
		);
		UUID userId = UUID.randomUUID();
		doThrow(new IllegalStateException("local listener failed"))
			.when(applicationEventPublisher)
			.publishEvent(new AuthSessionChangedEvent(userId));

		// when
		publisher.publish(userId);

		// then
		verify(redisTemplate).convertAndSend(AuthSessionChangePublisher.CHANNEL, userId.toString());
	}
}
