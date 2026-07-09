package com.team04.mopl.watching.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchingSessionStoreTest {

	private final WatchingSessionStore store = new WatchingSessionStore();

	@Test
	@DisplayName("시청자 추가 시 생성된 세션 정보를 반환한다")
	void addWatcher_returnsInfo_whenNewWatcher() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		Optional<WatchingSessionInfo> result = store.addWatcher(contentId, userId);

		assertThat(result).isPresent();
		assertThat(result.get().id()).isNotNull();
		assertThat(result.get().joinedAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 시청 중인 유저를 추가하면 empty를 반환한다")
	void addWatcher_returnsEmpty_whenAlreadyWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		store.addWatcher(contentId, userId);

		assertThat(store.addWatcher(contentId, userId)).isEmpty();
	}

	@Test
	@DisplayName("시청자의 세션 정보는 조회할 때마다 동일하게 유지된다")
	void addWatcher_infoIsStable_acrossReads() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		WatchingSessionInfo created = store.addWatcher(contentId, userId).orElseThrow();

		WatchingSessionInfo first = store.getWatchers(contentId).get(userId);
		WatchingSessionInfo second = store.getWatchers(contentId).get(userId);

		assertThat(first).isEqualTo(created);
		assertThat(second).isEqualTo(created);
	}

	@Test
	@DisplayName("시청자 제거 시 제거된 세션 정보를 반환한다")
	void removeWatcher_returnsInfo_whenWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		WatchingSessionInfo created = store.addWatcher(contentId, userId).orElseThrow();

		Optional<WatchingSessionInfo> removed = store.removeWatcher(contentId, userId);

		assertThat(removed).contains(created);
	}

	@Test
	@DisplayName("시청 중이 아닌 유저를 제거하면 empty를 반환한다")
	void removeWatcher_returnsEmpty_whenNotWatching() {
		assertThat(store.removeWatcher(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("시청자 수를 정확히 반환한다")
	void getWatcherCount_returnsCorrectCount() {
		UUID contentId = UUID.randomUUID();

		store.addWatcher(contentId, UUID.randomUUID());
		store.addWatcher(contentId, UUID.randomUUID());
		store.addWatcher(contentId, UUID.randomUUID());

		assertThat(store.getWatcherCount(contentId)).isEqualTo(3);
	}

	@Test
	@DisplayName("시청자가 없으면 0을 반환한다")
	void getWatcherCount_returnsZero_whenNoWatchers() {
		assertThat(store.getWatcherCount(UUID.randomUUID())).isZero();
	}

	@Test
	@DisplayName("모든 시청자 제거 후 watcherCount가 0이 된다")
	void removeWatcher_countsZero_whenAllRemoved() {
		UUID contentId = UUID.randomUUID();
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();

		store.addWatcher(contentId, userId1);
		store.addWatcher(contentId, userId2);
		store.removeWatcher(contentId, userId1);
		store.removeWatcher(contentId, userId2);

		assertThat(store.getWatcherCount(contentId)).isZero();
	}

	@Test
	@DisplayName("특정 유저가 시청 중인지 확인할 수 있다")
	void isWatching_returnsCorrectly() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		assertThat(store.isWatching(contentId, userId)).isFalse();

		store.addWatcher(contentId, userId);

		assertThat(store.isWatching(contentId, userId)).isTrue();
	}

	@Test
	@DisplayName("특정 유저가 시청 중인 모든 contentId를 조회할 수 있다")
	void getWatchingContentIds_returnsAllContentIds() {
		UUID userId = UUID.randomUUID();
		UUID contentId1 = UUID.randomUUID();
		UUID contentId2 = UUID.randomUUID();

		store.addWatcher(contentId1, userId);
		store.addWatcher(contentId2, userId);

		Set<UUID> result = store.getWatchingContentIds(userId);

		assertThat(result).containsExactlyInAnyOrder(contentId1, contentId2);
	}

	@Test
	@DisplayName("시청자 목록을 세션 정보와 함께 조회할 수 있다")
	void getWatchers_returnsWatcherInfoMap() {
		UUID contentId = UUID.randomUUID();
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();

		store.addWatcher(contentId, userId1);
		store.addWatcher(contentId, userId2);

		Map<UUID, WatchingSessionInfo> result = store.getWatchers(contentId);

		assertThat(result).containsOnlyKeys(userId1, userId2);
	}

	@Test
	@DisplayName("시청자가 없는 콘텐츠의 시청자 목록은 빈 Map을 반환한다")
	void getWatchers_returnsEmptyMap_whenNoWatchers() {
		assertThat(store.getWatchers(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("특정 유저의 시청 세션 전체를 contentId와 함께 조회할 수 있다")
	void getWatchingSessions_returnsContentInfoMap() {
		UUID userId = UUID.randomUUID();
		UUID contentId1 = UUID.randomUUID();
		UUID contentId2 = UUID.randomUUID();

		store.addWatcher(contentId1, userId);
		store.addWatcher(contentId2, userId);

		Map<UUID, WatchingSessionInfo> result = store.getWatchingSessions(userId);

		assertThat(result).containsOnlyKeys(contentId1, contentId2);
	}
}
