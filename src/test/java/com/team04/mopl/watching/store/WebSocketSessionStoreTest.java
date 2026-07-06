package com.team04.mopl.watching.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebSocketSessionStoreTest {

	private final WebSocketSessionStore store = new WebSocketSessionStore();

	@Test
	@DisplayName("세션 등록 후 userId를 조회할 수 있다")
	void register_thenGetUserId() {
		String sessionId = "session-1";
		UUID userId = UUID.randomUUID();

		store.register(sessionId, userId);

		assertThat(store.getUserId(sessionId)).contains(userId);
	}

	@Test
	@DisplayName("등록되지 않은 세션은 empty를 반환한다")
	void getUserId_returnsEmpty_whenNotRegistered() {
		assertThat(store.getUserId("unknown")).isEmpty();
	}

	@Test
	@DisplayName("세션 제거 후 조회하면 empty를 반환한다")
	void remove_thenGetUserIdReturnsEmpty() {
		String sessionId = "session-1";
		UUID userId = UUID.randomUUID();

		store.register(sessionId, userId);
		store.remove(sessionId);

		assertThat(store.getUserId(sessionId)).isEmpty();
	}
}
