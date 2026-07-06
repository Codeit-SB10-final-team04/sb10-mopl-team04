package com.team04.mopl.watching.store;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

// contentId별 시청 중인 유저와 세션 정보(id, joinedAt)를 관리하는 인메모리 저장소
// watcherCount의 유일한 진실의 원천(source of truth)
// DB의 Content.watcherCount 컬럼은 사용하지 않고, 이 Store에서 집계
// 모든 쓰기 연산은 compute 계열로 원자적으로 처리하여 add/remove 간 race condition 방지
// TODO: 다중 서버 환경에서는 Redis로 교체 필요
@Component
public class WatchingSessionStore {

	// contentId → (userId → 시청 세션 정보)
	private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, WatchingSessionInfo>> contentWatcherMap
		= new ConcurrentHashMap<>();

	// 시청자 추가, 새로 추가되면 생성된 세션 정보 반환, 이미 시청 중이면 empty 반환
	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId) {
		AtomicReference<WatchingSessionInfo> created = new AtomicReference<>();

		contentWatcherMap.compute(contentId, (key, watchers) -> {
			if (watchers == null) {
				watchers = new ConcurrentHashMap<>();
			}

			watchers.computeIfAbsent(userId, id -> {
				WatchingSessionInfo info = WatchingSessionInfo.create();
				created.set(info);
				return info;
			});

			return watchers;
		});

		return Optional.ofNullable(created.get());
	}

	// 시청자 제거, 제거된 세션 정보 반환, 시청 중이 아니었으면 empty 반환
	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId) {
		AtomicReference<WatchingSessionInfo> removed = new AtomicReference<>();

		contentWatcherMap.computeIfPresent(contentId, (key, watchers) -> {
			removed.set(watchers.remove(userId));

			// 시청자가 없으면 Map에서 제거 (compute 내부라 addWatcher와 원자적으로 동작)
			return watchers.isEmpty() ? null : watchers;
		});

		return Optional.ofNullable(removed.get());
	}

	// 시청자 수 조회
	public long getWatcherCount(UUID contentId) {
		Map<UUID, WatchingSessionInfo> watchers = contentWatcherMap.get(contentId);

		return watchers == null ? 0 : watchers.size();
	}

	// 특정 콘텐츠의 시청자 목록 조회 (userId → 세션 정보)
	public Map<UUID, WatchingSessionInfo> getWatchers(UUID contentId) {
		Map<UUID, WatchingSessionInfo> watchers = contentWatcherMap.get(contentId);

		return watchers == null ? Map.of() : Map.copyOf(watchers);
	}

	// 특정 유저가 시청 중인지 확인
	public boolean isWatching(UUID contentId, UUID userId) {
		Map<UUID, WatchingSessionInfo> watchers = contentWatcherMap.get(contentId);

		return watchers != null && watchers.containsKey(userId);
	}

	// 특정 유저가 시청 중인 모든 contentId 조회 (DISCONNECT 시 정리용)
	public Set<UUID> getWatchingContentIds(UUID userId) {
		Set<UUID> result = ConcurrentHashMap.newKeySet();

		contentWatcherMap.forEach((contentId, watchers) -> {
			if (watchers.containsKey(userId)) {
				result.add(contentId);
			}
		});

		return result;
	}

	// 특정 유저의 시청 세션 전체 조회 (contentId → 세션 정보), 유저별 단건 조회 시 사용
	public Map<UUID, WatchingSessionInfo> getWatchingSessions(UUID userId) {
		Map<UUID, WatchingSessionInfo> result = new ConcurrentHashMap<>();

		contentWatcherMap.forEach((contentId, watchers) -> {
			WatchingSessionInfo info = watchers.get(userId);
			if (info != null) {
				result.put(contentId, info);
			}
		});

		return result;
	}
}
