package com.team04.mopl.watching.store;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// STOMP 세션 ID ↔ 유저 ID 매핑을 관리하는 인메모리 저장소
// DISCONNECT 시 Principal이 null일 수 있어서, 세션 ID로 어떤 유저인지 역추적하기 위해 필요
// TODO: 다중 서버 환경에서는 Redis로 교체 필요
@Component
public class WebSocketSessionStore {

	private final ConcurrentHashMap<String, UUID> sessionUserMap = new ConcurrentHashMap<>();

	public void register(String sessionId, UUID userId) {
		sessionUserMap.put(sessionId, userId);
	}

	public Optional<UUID> getUserId(String sessionId) {
		return Optional.ofNullable(sessionUserMap.get(sessionId));
	}

	public void remove(String sessionId) {
		sessionUserMap.remove(sessionId);
	}
}
