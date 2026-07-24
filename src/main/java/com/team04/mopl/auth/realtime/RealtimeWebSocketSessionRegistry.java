package com.team04.mopl.auth.realtime;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RealtimeWebSocketSessionRegistry {

	private final ConcurrentHashMap<String, WebSocketSession> transports = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AuthenticatedSession> authenticatedSessions = new ConcurrentHashMap<>();

	// WebSocket 전송 계층의 연결 생명주기 추적
	public WebSocketHandlerDecoratorFactory decoratorFactory() {
		return handler -> new TrackingWebSocketHandlerDecorator(handler);
	}

	// STOMP 인증 세션과 WebSocket 전송 세션 바인딩
	public boolean bind(String transportSessionId, UUID userId, UUID authSessionId) {
		if (!transports.containsKey(transportSessionId)) {
			log.warn("[WEBSOCKET_AUTH_SESSION_BIND_FAILED] WebSocket 전송 세션을 찾을 수 없음: sessionId={}",
				transportSessionId);
			return false;
		}

		authenticatedSessions.put(
			transportSessionId,
			new AuthenticatedSession(transportSessionId, userId, authSessionId)
		);
		return true;
	}

	public List<AuthenticatedSession> findByUserId(UUID userId) {
		return authenticatedSessions.values().stream()
			.filter(session -> session.userId().equals(userId))
			.toList();
	}

	// 정책 위반 상태 코드를 통한 무효 WebSocket 연결 종료
	public boolean close(String transportSessionId) {
		WebSocketSession session = transports.get(transportSessionId);

		if (session == null || !session.isOpen()) {
			remove(transportSessionId);
			return false;
		}

		try {
			session.close(CloseStatus.POLICY_VIOLATION);
			remove(transportSessionId);
			return true;
		} catch (IOException | RuntimeException exception) {
			log.warn("[WEBSOCKET_AUTH_SESSION_CLOSE_FAILED] WebSocket 강제 종료 실패: sessionId={}",
				transportSessionId, exception);
			return false;
		}
	}

	// WebSocket 전송 세션과 인증 세션 추적 정보 제거
	public void remove(String transportSessionId) {
		transports.remove(transportSessionId);
		authenticatedSessions.remove(transportSessionId);
	}

	// 현재 추적 중인 인증 완료 WebSocket 세션 수 조회
	public int authenticatedSessionCount() {
		return authenticatedSessions.size();
	}

	public record AuthenticatedSession(
		String transportSessionId,
		UUID userId,
		UUID authSessionId
	) {
	}

	// WebSocket 연결 생성/종료 생명주기 추적 및 정리
	private final class TrackingWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

		private TrackingWebSocketHandlerDecorator(WebSocketHandler delegate) {
			super(delegate);
		}

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws Exception {
			// STOMP CONNECT 이전 WebSocket 전송 세션 등록
			transports.put(session.getId(), session);

			try {
				super.afterConnectionEstablished(session);
			} catch (Exception exception) {
				remove(session.getId());
				throw exception;
			}
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
			try {
				super.afterConnectionClosed(session, closeStatus);
			} finally {
				// 연결 종료 후 전송 및 인증 세션 정리
				remove(session.getId());
			}
		}
	}
}
