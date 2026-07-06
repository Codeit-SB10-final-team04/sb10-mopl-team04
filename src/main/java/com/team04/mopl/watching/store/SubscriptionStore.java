package com.team04.mopl.watching.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// 구독 ID ↔ destination 매핑을 관리하는 인메모리 저장소
// UNSUBSCRIBE 프레임에는 destination이 없고 구독 ID만 오므로, 어떤 콘텐츠인지 역추적하기 위해 필요
// 키: "sessionId:subscriptionId", 값: destination
// TODO: 다중 서버 환경에서는 Redis로 교체 필요
@Component
public class SubscriptionStore {

	private final ConcurrentHashMap<String, String> subscriptionMap = new ConcurrentHashMap<>();

	public void register(String sessionId, String subscriptionId, String destination) {
		subscriptionMap.put(toKey(sessionId, subscriptionId), destination);
	}

	public Optional<String> getDestination(String sessionId, String subscriptionId) {
		return Optional.ofNullable(subscriptionMap.get(toKey(sessionId, subscriptionId)));
	}

	public void remove(String sessionId, String subscriptionId) {
		subscriptionMap.remove(toKey(sessionId, subscriptionId));
	}

	// 특정 세션의 모든 구독 제거 (DISCONNECT 시 사용)
	public void removeAllBySession(String sessionId) {
		String prefix = sessionId + ":";
		subscriptionMap.keySet().removeIf(key -> key.startsWith(prefix));
	}

	// 특정 세션의 모든 구독 조회 (DISCONNECT 시 정리용)
	public Map<String, String> getAllBySession(String sessionId) {
		String prefix = sessionId + ":";
		ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();

		subscriptionMap.forEach((key, value) -> {
			if (key.startsWith(prefix)) {
				result.put(key, value);
			}
		});

		return result;
	}

	private String toKey(String sessionId, String subscriptionId) {
		return sessionId + ":" + subscriptionId;
	}
}
