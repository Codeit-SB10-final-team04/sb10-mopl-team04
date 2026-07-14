package com.team04.mopl.watching.store;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 구독 ID ↔ destination 매핑을 관리하는 Redis 저장소
// UNSUBSCRIBE 프레임에는 destination이 없고 구독 ID만 오므로, 어떤 콘텐츠인지 역추적하기 위해 필요
// 키: stomp:sub:{sessionId}:{subscriptionId} → destination (TTL 24시간)
@Component
@RequiredArgsConstructor
public class SubscriptionStore {

	private final StringRedisTemplate redisTemplate;
	private static final String KEY_PREFIX = "stomp:sub:";

	public void register(String sessionId, String subscriptionId, String destination) {
		redisTemplate.opsForValue().set(toKey(sessionId, subscriptionId), destination, 24, TimeUnit.HOURS);
	}

	public Optional<String> getDestination(String sessionId, String subscriptionId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(toKey(sessionId, subscriptionId)));
	}

	public void remove(String sessionId, String subscriptionId) {
		redisTemplate.delete(toKey(sessionId, subscriptionId));
	}

	public void removeAllBySession(String sessionId) {
		Set<String> keys = redisTemplate.keys(KEY_PREFIX + sessionId + ":*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	private String toKey(String sessionId, String subscriptionId) {
		return KEY_PREFIX + sessionId + ":" + subscriptionId;
	}
}
