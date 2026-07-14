package com.team04.mopl.common.redis;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.team04.mopl.config.RedisMessageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// STOMP 메시지를 Redis Pub/Sub으로 발행, Redis 없으면 로컬 브로커 fallback
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher {

	// 서버 인스턴스 고유 ID — 자기가 발행한 메시지를 중복 수신하지 않기 위한 식별자
	static final String SERVER_ID = UUID.randomUUID().toString();

	private final SimpMessagingTemplate messagingTemplate;
	private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

	public void publish(String destination, Object payload) {
		RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();

		if (redisTemplate != null) {
			try {
				redisTemplate.convertAndSend(
					RedisMessageConfig.STOMP_BROADCAST_CHANNEL,
					new StompBroadcastMessage(SERVER_ID, destination, payload)
				);
				// 로컬 클라이언트에게도 직접 전달 (Redis 구독에서는 자기 메시지 스킵)
				messagingTemplate.convertAndSend(destination, payload);
				log.debug("[REDIS_PUB] destination={}", destination);
				return;
			} catch (Exception e) {
				log.warn("[REDIS_PUB] 발행 실패, 로컬 fallback: destination={}", destination, e);
			}
		}

		messagingTemplate.convertAndSend(destination, payload);
	}
}
