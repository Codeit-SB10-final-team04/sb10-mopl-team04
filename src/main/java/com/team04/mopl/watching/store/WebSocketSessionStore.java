package com.team04.mopl.watching.store;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// STOMP 세션 ID ↔ 유저 ID 매핑을 관리하는 Redis 저장소
// DISCONNECT 시 Principal이 null일 수 있어서, 세션 ID로 어떤 유저인지 역추적하기 위해 필요
// 키: stomp:session:{sessionId} → userId
// TTL 24시간: 정상 종료 시 DISCONNECT 핸들러에서 즉시 삭제, 비정상 종료 시 TTL로 자동 정리
@Component
@RequiredArgsConstructor
public class WebSocketSessionStore {

	private final StringRedisTemplate redisTemplate;
	private static final String KEY_PREFIX = "stomp:session:";

	public void register(String sessionId, UUID userId) {
		redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, userId.toString(), 24, TimeUnit.HOURS);
	}

	public Optional<UUID> getUserId(String sessionId) {
		String value = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
		return value != null ? Optional.of(UUID.fromString(value)) : Optional.empty();
	}

	public void remove(String sessionId) {
		redisTemplate.delete(KEY_PREFIX + sessionId);
	}
}
