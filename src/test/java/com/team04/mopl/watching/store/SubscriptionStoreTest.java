package com.team04.mopl.watching.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionStoreTest {

	private final SubscriptionStore store = new SubscriptionStore();

	@Test
	@DisplayName("구독 등록 후 destination을 조회할 수 있다")
	void register_thenGetDestination() {
		store.register("session-1", "sub-0", "/sub/contents/123/watch");

		assertThat(store.getDestination("session-1", "sub-0"))
			.contains("/sub/contents/123/watch");
	}

	@Test
	@DisplayName("등록되지 않은 구독은 empty를 반환한다")
	void getDestination_returnsEmpty_whenNotRegistered() {
		assertThat(store.getDestination("session-1", "sub-0")).isEmpty();
	}

	@Test
	@DisplayName("구독 제거 후 조회하면 empty를 반환한다")
	void remove_thenGetDestinationReturnsEmpty() {
		store.register("session-1", "sub-0", "/sub/contents/123/watch");
		store.remove("session-1", "sub-0");

		assertThat(store.getDestination("session-1", "sub-0")).isEmpty();
	}

	@Test
	@DisplayName("특정 세션의 모든 구독을 제거할 수 있다")
	void removeAllBySession_removesOnlyTargetSession() {
		store.register("session-1", "sub-0", "/sub/contents/123/watch");
		store.register("session-1", "sub-1", "/sub/contents/123/chat");
		store.register("session-2", "sub-0", "/sub/contents/456/watch");

		store.removeAllBySession("session-1");

		assertThat(store.getDestination("session-1", "sub-0")).isEmpty();
		assertThat(store.getDestination("session-1", "sub-1")).isEmpty();
		assertThat(store.getDestination("session-2", "sub-0")).contains("/sub/contents/456/watch");
	}
}
