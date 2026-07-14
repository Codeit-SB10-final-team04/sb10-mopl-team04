package com.team04.mopl.watching.store;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 구독 ID ↔ destination 매핑을 관리하는 Redis 저장소
// 키 구조:
//   stomp:sub:{sessionId}:{subscriptionId} → destination (TTL 24시간)
//   stomp:sub-index:{sessionId} → Set<subscriptionId> (KEYS 대신 역인덱스)
@Component
@RequiredArgsConstructor
public class SubscriptionStore {

	private final StringRedisTemplate redisTemplate;
	private static final String KEY_PREFIX = "stomp:sub:";
	private static final String INDEX_PREFIX = "stomp:sub-index:";

	public void register(String sessionId, String subscriptionId, String destination) {
		redisTemplate.opsForValue().set(toKey(sessionId, subscriptionId), destination, 24, TimeUnit.HOURS);
		redisTemplate.opsForSet().add(INDEX_PREFIX + sessionId, subscriptionId);
		redisTemplate.expire(INDEX_PREFIX + sessionId, 24, TimeUnit.HOURS);
	}

	public Optional<String> getDestination(String sessionId, String subscriptionId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(toKey(sessionId, subscriptionId)));
	}

	public void remove(String sessionId, String subscriptionId) {
		redisTemplate.delete(toKey(sessionId, subscriptionId));
		redisTemplate.opsForSet().remove(INDEX_PREFIX + sessionId, subscriptionId);
	}

	// KEYS 명령 대신 역인덱스 Set으로 세션의 모든 구독 제거
	public void removeAllBySession(String sessionId) {
		Set<String> subscriptionIds = redisTemplate.opsForSet().members(INDEX_PREFIX + sessionId);
		if (subscriptionIds != null) {
			for (String subId : subscriptionIds) {
				redisTemplate.delete(toKey(sessionId, subId));
			}
		}
		redisTemplate.delete(INDEX_PREFIX + sessionId);
	}

	private String toKey(String sessionId, String subscriptionId) {
		return KEY_PREFIX + sessionId + ":" + subscriptionId;
	}
}
