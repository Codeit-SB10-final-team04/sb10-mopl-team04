package com.team04.mopl.sse.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

	private final ConcurrentHashMap<UUID, List<SseEmitter>> data = new ConcurrentHashMap<>();

	public void add(UUID receiverId, SseEmitter sseEmitter) {
		// 기존 receiverId가 없다면 새로 생성 후 sseEmitter 저장하고, 이미 있다면 가져옴
		data.computeIfAbsent(
				receiverId,
				id -> new CopyOnWriteArrayList<>()
			)
			.add(sseEmitter);
	}

	public void remove(UUID receiverId, SseEmitter sseEmitter) {
		// receiverId라는 key가 data에 존재할 경우, 해당 key 값을 새로 계산 후 갱신
		data.computeIfPresent(
			receiverId,
			(id, sseEmitterList) -> {
				sseEmitterList.remove(sseEmitter);
				return sseEmitterList.isEmpty()
					? null // null 반환 시 receiverId(key)가 Map에서 삭제됨
					: sseEmitterList;
			}
		);
	}

	public List<SseEmitter> findAllByReceiverId(UUID receiverId) {
		return data.getOrDefault(receiverId, List.of());
	}

	public Map<UUID, List<SseEmitter>> findAll() {
		return Map.copyOf(data);
	}
}
