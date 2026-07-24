package com.team04.mopl.auth.realtime;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthSessionChangePublisher {

	public static final String CHANNEL = "mopl:auth-session:changed";

	private final ApplicationEventPublisher applicationEventPublisher;
	private final StringRedisTemplate redisTemplate;
	private final boolean redisPubSubEnabled;

	// 인증 세션 변경 전파
	public AuthSessionChangePublisher(
		ApplicationEventPublisher applicationEventPublisher,
		StringRedisTemplate redisTemplate,
		@Value("${mopl.redis.pubsub.enabled:true}") boolean redisPubSubEnabled
	) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.redisTemplate = redisTemplate;
		this.redisPubSubEnabled = redisPubSubEnabled;
	}

	// 현재 인스턴스 정리 및 다른 인스턴스로 인증 세션 변경 전파
	public void publish(UUID userId) {
		AuthSessionChangedEvent event = new AuthSessionChangedEvent(userId);

		// 현재 인스턴스의 무효 실시간 연결 즉시 정리
		try {
			applicationEventPublisher.publishEvent(event);
		} catch (RuntimeException exception) {
			// 부가 정리 실패에 대한 인증 세션 변경 결과 유지
			log.error("[AUTH_SESSION_CHANGE_LOCAL_PUBLISH_FAILED] 인증 세션 변경 로컬 전파 실패: userId={}",
				userId, exception);
		}

		// Redis Pub/Sub 비활성 환경의 로컬 정리로 종료
		if (!redisPubSubEnabled) {
			return;
		}

		// 다른 인스턴스의 무효 실시간 연결 정리 요청
		try {
			redisTemplate.convertAndSend(CHANNEL, userId.toString());
		} catch (RuntimeException exception) {
			// 전파 실패에 대한 인증 세션 변경 결과 유지
			log.error("[AUTH_SESSION_CHANGE_PUBLISH_FAILED] 인증 세션 변경 전파 실패: userId={}",
				userId, exception);
		}
	}
}
