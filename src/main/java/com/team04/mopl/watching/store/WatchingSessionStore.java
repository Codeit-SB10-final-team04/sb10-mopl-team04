package com.team04.mopl.watching.store;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// contentId별 시청 중인 유저를 관리하는 인메모리 저장소
// watcherCount의 유일한 진실의 원천(source of truth)
// DB의 Content.watcherCount 컬럼은 사용하지 않고, 이 Store에서 집계
// TODO: 다중 서버 환경에서는 Redis로 교체 필요
@Component
public class WatchingSessionStore {

	// contentId → 시청 중인 유저 ID 집합
	private final ConcurrentHashMap<UUID, Set<UUID>> contentWatcherMap = new ConcurrentHashMap<>();

	// 시청자 추가, 이미 있으면 false 반환
	public boolean addWatcher(UUID contentId, UUID userId) {
		Set<UUID> watchers = contentWatcherMap.computeIfAbsent(
			contentId,
			key -> ConcurrentHashMap.newKeySet()
		);

		return watchers.add(userId);
	}

	// 시청자 제거, 없었으면 false 반환
	public boolean removeWatcher(UUID contentId, UUID userId) {
		Set<UUID> watchers = contentWatcherMap.get(contentId);

		if (watchers == null) {
			return false;
		}

		boolean removed = watchers.remove(userId);

		// 시청자가 없으면 Map에서 제거
		if (watchers.isEmpty()) {
			contentWatcherMap.remove(contentId);
		}

		return removed;
	}

	// 시청자 수 조회
	public long getWatcherCount(UUID contentId) {
		Set<UUID> watchers = contentWatcherMap.get(contentId);

		return watchers == null ? 0 : watchers.size();
	}

	// 특정 콘텐츠의 시청자 목록 조회
	public Set<UUID> getWatchers(UUID contentId) {
		Set<UUID> watchers = contentWatcherMap.get(contentId);

		return watchers == null ? Set.of() : Set.copyOf(watchers);
	}

	// 특정 유저가 시청 중인지 확인
	public boolean isWatching(UUID contentId, UUID userId) {
		Set<UUID> watchers = contentWatcherMap.get(contentId);

		return watchers != null && watchers.contains(userId);
	}

	// 특정 유저가 시청 중인 모든 contentId 조회 (DISCONNECT 시 정리용)
	public Set<UUID> getWatchingContentIds(UUID userId) {
		Set<UUID> result = ConcurrentHashMap.newKeySet();

		contentWatcherMap.forEach((contentId, watchers) -> {
			if (watchers.contains(userId)) {
				result.add(contentId);
			}
		});

		return result;
	}
}
