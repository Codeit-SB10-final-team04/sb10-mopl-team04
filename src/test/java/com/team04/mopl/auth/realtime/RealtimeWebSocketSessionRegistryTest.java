package com.team04.mopl.auth.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class RealtimeWebSocketSessionRegistryTest {

	@Test
	@DisplayName("WebSocket 전송 세션을 인증 세션에 연결하고 정책 위반 상태로 종료한다")
	void registry_tracksBindsAndClosesWebSocketSession() throws Exception {
		// given
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();
		WebSocketHandler delegate = mock(WebSocketHandler.class);
		WebSocketHandler decorated = registry.decoratorFactory().decorate(delegate);
		WebSocketSession webSocketSession = mock(WebSocketSession.class);
		UUID userId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();

		when(webSocketSession.getId()).thenReturn("transport-session");
		when(webSocketSession.isOpen()).thenReturn(true);

		// when
		decorated.afterConnectionEstablished(webSocketSession);
		boolean bound = registry.bind("transport-session", userId, authSessionId);
		assertThat(registry.findByUserId(userId))
			.containsExactly(new RealtimeWebSocketSessionRegistry.AuthenticatedSession(
				"transport-session",
				userId,
				authSessionId
			));
		boolean closed = registry.close("transport-session");

		// then
		assertThat(bound).isTrue();
		assertThat(closed).isTrue();
		assertThat(registry.findByUserId(userId)).isEmpty();
		verify(delegate).afterConnectionEstablished(webSocketSession);
		verify(webSocketSession).close(CloseStatus.POLICY_VIOLATION);

		// when
		decorated.afterConnectionClosed(webSocketSession, CloseStatus.NORMAL);

		// then
		assertThat(registry.authenticatedSessionCount()).isZero();
	}

	@Test
	@DisplayName("추적되지 않은 WebSocket 전송 세션은 인증 세션에 연결하지 않는다")
	void bind_returnsFalse_whenTransportSessionDoesNotExist() {
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();

		boolean result = registry.bind(
			"missing-session",
			UUID.randomUUID(),
			UUID.randomUUID()
		);

		assertThat(result).isFalse();
		assertThat(registry.authenticatedSessionCount()).isZero();
	}

	@Test
	@DisplayName("이미 닫힌 WebSocket은 레지스트리에서 제거하고 다시 닫지 않는다")
	void close_removesSessionWithoutClosing_whenWebSocketIsAlreadyClosed() throws Exception {
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();
		WebSocketHandler delegate = mock(WebSocketHandler.class);
		WebSocketHandler decorated = registry.decoratorFactory().decorate(delegate);
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("closed-session");
		when(session.isOpen()).thenReturn(false);
		decorated.afterConnectionEstablished(session);
		registry.bind("closed-session", UUID.randomUUID(), UUID.randomUUID());

		boolean result = registry.close("closed-session");

		assertThat(result).isFalse();
		assertThat(registry.authenticatedSessionCount()).isZero();
	}

	@Test
	@DisplayName("WebSocket 강제 종료가 실패하면 실패를 반환하고 세션을 유지한다")
	void close_returnsFalse_whenWebSocketCloseFails() throws Exception {
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();
		WebSocketHandler delegate = mock(WebSocketHandler.class);
		WebSocketHandler decorated = registry.decoratorFactory().decorate(delegate);
		WebSocketSession session = mock(WebSocketSession.class);
		UUID userId = UUID.randomUUID();
		when(session.getId()).thenReturn("failing-session");
		when(session.isOpen()).thenReturn(true);
		doThrow(new IOException("close failed"))
			.when(session)
			.close(CloseStatus.POLICY_VIOLATION);
		decorated.afterConnectionEstablished(session);
		registry.bind("failing-session", userId, UUID.randomUUID());

		boolean result = registry.close("failing-session");

		assertThat(result).isFalse();
		assertThat(registry.findByUserId(userId)).hasSize(1);
	}

	@Test
	@DisplayName("WebSocket 연결 완료 위임이 실패하면 추적 정보를 제거한다")
	void decorator_removesSession_whenConnectionEstablishedDelegateFails() throws Exception {
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();
		WebSocketHandler delegate = mock(WebSocketHandler.class);
		WebSocketHandler decorated = registry.decoratorFactory().decorate(delegate);
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("establish-failing-session");
		doThrow(new IllegalStateException("delegate failed"))
			.when(delegate)
			.afterConnectionEstablished(session);

		assertThatThrownBy(() -> decorated.afterConnectionEstablished(session))
			.isInstanceOf(IllegalStateException.class);
		assertThat(registry.bind(
			"establish-failing-session",
			UUID.randomUUID(),
			UUID.randomUUID()
		)).isFalse();
	}

	@Test
	@DisplayName("WebSocket 종료 위임이 실패해도 추적 정보를 제거한다")
	void decorator_removesSession_whenConnectionClosedDelegateFails() throws Exception {
		RealtimeWebSocketSessionRegistry registry = new RealtimeWebSocketSessionRegistry();
		WebSocketHandler delegate = mock(WebSocketHandler.class);
		WebSocketHandler decorated = registry.decoratorFactory().decorate(delegate);
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("close-delegate-failing-session");
		decorated.afterConnectionEstablished(session);
		registry.bind("close-delegate-failing-session", UUID.randomUUID(), UUID.randomUUID());
		doThrow(new IllegalStateException("delegate failed"))
			.when(delegate)
			.afterConnectionClosed(session, CloseStatus.NORMAL);

		assertThatThrownBy(() -> decorated.afterConnectionClosed(session, CloseStatus.NORMAL))
			.isInstanceOf(IllegalStateException.class);
		assertThat(registry.authenticatedSessionCount()).isZero();
	}
}
