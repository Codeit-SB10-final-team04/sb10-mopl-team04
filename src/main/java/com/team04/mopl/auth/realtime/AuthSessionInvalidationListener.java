package com.team04.mopl.auth.realtime;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.session.AuthSessionStore;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionInvalidationListener {

	private final AuthSessionStore authSessionStore;
	private final RealtimeWebSocketSessionRegistry webSocketSessionRegistry;
	private final RealtimeSseSessionRegistry sseSessionRegistry;
	private final MeterRegistry meterRegistry;

	// 사용자별 무효 WebSocket 및 SSE 연결 정리
	@EventListener
	public void handle(AuthSessionChangedEvent event) {
		UUID userId = event.userId();
		int closedWebSockets = closeInactiveWebSockets(userId);
		int closedSseConnections = closeInactiveSseConnections(userId);

		if (closedWebSockets > 0 || closedSseConnections > 0) {
			log.info(
				"[AUTH_SESSION_REALTIME_INVALIDATED] 무효 실시간 연결 종료: userId={}, webSockets={}, sse={}",
				userId,
				closedWebSockets,
				closedSseConnections
			);
		}
	}

	// 사용자의 무효 WebSocket 연결 조회 및 종료
	private int closeInactiveWebSockets(UUID userId) {
		int closed = 0;

		for (RealtimeWebSocketSessionRegistry.AuthenticatedSession session
			: webSocketSessionRegistry.findByUserId(userId)) {
			if (!isActive(userId, session.authSessionId())
				&& webSocketSessionRegistry.close(session.transportSessionId())) {
				closed++;
				meterRegistry.counter(
					"mopl.auth.session.realtime.invalidated",
					"transport", "websocket"
				).increment();
			}
		}

		return closed;
	}

	// 사용자의 무효 SSE 연결 조회 및 종료
	private int closeInactiveSseConnections(UUID userId) {
		int closed = 0;

		for (RealtimeSseSessionRegistry.AuthenticatedSession session
			: sseSessionRegistry.findByUserId(userId)) {
			if (isActive(userId, session.authSessionId())) {
				continue;
			}

			if (sseSessionRegistry.close(session)) {
				closed++;
				meterRegistry.counter(
					"mopl.auth.session.realtime.invalidated",
					"transport", "sse"
				).increment();
			}
		}

		return closed;
	}

	// Redis 기준 인증 세션 활성 여부 확인
	private boolean isActive(UUID userId, UUID authSessionId) {
		try {
			return authSessionStore.isActive(userId, authSessionId);
		} catch (AuthException exception) {
			// 검증 장애 시 기존 연결 보수적 종료
			log.warn("[AUTH_SESSION_REALTIME_VALIDATE_FAILED] 실시간 연결 인증 세션 검증 실패: userId={}, authSessionId={}",
				userId, authSessionId, exception);
			return false;
		}
	}
}
