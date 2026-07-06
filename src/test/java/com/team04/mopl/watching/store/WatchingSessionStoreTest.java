package com.team04.mopl.watching.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchingSessionStoreTest {

	private final WatchingSessionStore store = new WatchingSessionStore();

	@Test
	@DisplayName("시청자 추가 시 true를 반환한다")
	void addWatcher_returnsTrue_whenNewWatcher() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		assertThat(store.addWatcher(contentId, userId)).isTrue();
	}

	@Test
	@DisplayName("이미 시청 중인 유저를 추가하면 false를 반환한다")
	void addWatcher_returnsFalse_whenAlreadyWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		store.addWatcher(contentId, userId);

		assertThat(store.addWatcher(contentId, userId)).isFalse();
	}

	@Test
	@DisplayName("시청자 제거 시 true를 반환한다")
	void removeWatcher_returnsTrue_whenWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		store.addWatcher(contentId, userId);

		assertThat(store.removeWatcher(contentId, userId)).isTrue();
	}

	@Test
	@DisplayName("시청 중이 아닌 유저를 제거하면 false를 반환한다")
	void removeWatcher_returnsFalse_whenNotWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		assertThat(store.removeWatcher(contentId, userId)).isFalse();
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
	@DisplayName("시청자 목록을 조회할 수 있다")
	void getWatchers_returnsWatcherSet() {
		UUID contentId = UUID.randomUUID();
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();

		store.addWatcher(contentId, userId1);
		store.addWatcher(contentId, userId2);

		Set<UUID> result = store.getWatchers(contentId);

		assertThat(result).containsExactlyInAnyOrder(userId1, userId2);
	}

	@Test
	@DisplayName("시청자가 없는 콘텐츠의 시청자 목록은 빈 Set을 반환한다")
	void getWatchers_returnsEmptySet_whenNoWatchers() {
		assertThat(store.getWatchers(UUID.randomUUID())).isEmpty();
	}
}
