package com.team04.mopl.auth.realtime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.session.AuthSessionStore;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class AuthSessionInvalidationListenerTest {

	@Mock
	private AuthSessionStore authSessionStore;
	@Mock
	private RealtimeWebSocketSessionRegistry webSocketSessionRegistry;
	@Mock
	private RealtimeSseSessionRegistry sseSessionRegistry;
	private AuthSessionInvalidationListener listener;

	@BeforeEach
	void setUp() {
		listener = new AuthSessionInvalidationListener(
			authSessionStore,
			webSocketSessionRegistry,
			sseSessionRegistry,
			new SimpleMeterRegistry()
		);
	}

	@Test
	@DisplayName("인증 세션이 교체되면 이전 WebSocket과 SSE만 종료하고 현재 세션 연결은 유지한다")
	void handle_closesOnlyInactiveRealtimeConnections_whenSessionIsReplaced() {
		// given
		UUID userId = UUID.randomUUID();
		UUID previousSessionId = UUID.randomUUID();
		UUID currentSessionId = UUID.randomUUID();
		SseEmitter previousEmitter = mock(SseEmitter.class);
		SseEmitter currentEmitter = mock(SseEmitter.class);

		RealtimeWebSocketSessionRegistry.AuthenticatedSession previousWebSocket =
			new RealtimeWebSocketSessionRegistry.AuthenticatedSession(
				"previous-websocket",
				userId,
				previousSessionId
			);
		RealtimeWebSocketSessionRegistry.AuthenticatedSession currentWebSocket =
			new RealtimeWebSocketSessionRegistry.AuthenticatedSession(
				"current-websocket",
				userId,
				currentSessionId
			);

		given(webSocketSessionRegistry.findByUserId(userId))
			.willReturn(List.of(previousWebSocket, currentWebSocket));
		RealtimeSseSessionRegistry.AuthenticatedSession previousSse =
			new RealtimeSseSessionRegistry.AuthenticatedSession(
				userId,
				previousSessionId,
				previousEmitter
			);
		RealtimeSseSessionRegistry.AuthenticatedSession currentSse =
			new RealtimeSseSessionRegistry.AuthenticatedSession(
				userId,
				currentSessionId,
				currentEmitter
			);

		given(sseSessionRegistry.findByUserId(userId))
			.willReturn(List.of(
				previousSse,
				currentSse
			));
		given(authSessionStore.isActive(userId, previousSessionId)).willReturn(false);
		given(authSessionStore.isActive(userId, currentSessionId)).willReturn(true);
		given(webSocketSessionRegistry.close("previous-websocket")).willReturn(true);
		given(sseSessionRegistry.close(previousSse)).willReturn(true);

		// when
		listener.handle(new AuthSessionChangedEvent(userId));

		// then
		verify(webSocketSessionRegistry).close("previous-websocket");
		verify(webSocketSessionRegistry, never()).close("current-websocket");
		verify(sseSessionRegistry).close(previousSse);
		verify(sseSessionRegistry, never()).close(currentSse);
	}

	@Test
	@DisplayName("인증 세션 검증이 실패하면 WebSocket과 SSE를 보수적으로 종료한다")
	void handle_closesRealtimeConnections_whenSessionValidationFails() {
		// given
		UUID userId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		RealtimeWebSocketSessionRegistry.AuthenticatedSession webSocket =
			new RealtimeWebSocketSessionRegistry.AuthenticatedSession(
				"websocket",
				userId,
				authSessionId
			);
		RealtimeSseSessionRegistry.AuthenticatedSession sse =
			new RealtimeSseSessionRegistry.AuthenticatedSession(
				userId,
				authSessionId,
				mock(SseEmitter.class)
			);
		given(webSocketSessionRegistry.findByUserId(userId)).willReturn(List.of(webSocket));
		given(sseSessionRegistry.findByUserId(userId)).willReturn(List.of(sse));
		given(authSessionStore.isActive(userId, authSessionId))
			.willThrow(new AuthException(AuthErrorCode.AUTH_SERVICE_ERROR));
		given(webSocketSessionRegistry.close("websocket")).willReturn(true);
		given(sseSessionRegistry.close(sse)).willReturn(true);

		// when
		listener.handle(new AuthSessionChangedEvent(userId));

		// then
		verify(webSocketSessionRegistry).close("websocket");
		verify(sseSessionRegistry).close(sse);
	}

	@Test
	@DisplayName("무효 연결 종료가 실패하면 종료 성공으로 집계하지 않는다")
	void handle_doesNotCountConnection_whenTransportCloseFails() {
		// given
		UUID userId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		RealtimeWebSocketSessionRegistry.AuthenticatedSession webSocket =
			new RealtimeWebSocketSessionRegistry.AuthenticatedSession(
				"websocket",
				userId,
				authSessionId
			);
		RealtimeSseSessionRegistry.AuthenticatedSession sse =
			new RealtimeSseSessionRegistry.AuthenticatedSession(
				userId,
				authSessionId,
				mock(SseEmitter.class)
			);
		given(webSocketSessionRegistry.findByUserId(userId)).willReturn(List.of(webSocket));
		given(sseSessionRegistry.findByUserId(userId)).willReturn(List.of(sse));
		given(authSessionStore.isActive(userId, authSessionId)).willReturn(false);
		given(webSocketSessionRegistry.close("websocket")).willReturn(false);
		given(sseSessionRegistry.close(sse)).willReturn(false);

		// when
		listener.handle(new AuthSessionChangedEvent(userId));

		// then
		verify(webSocketSessionRegistry).close("websocket");
		verify(sseSessionRegistry).close(sse);
	}
}
