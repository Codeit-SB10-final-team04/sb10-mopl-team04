package com.team04.mopl.common.stomp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

	@Spy
	private MeterRegistry meterRegistry = new SimpleMeterRegistry();

	@InjectMocks
	private WebSocketEventListener webSocketEventListener;

	@BeforeEach
	void setUp() {
		webSocketEventListener.init();
	}

	@Test
	@DisplayName("성공: 웹소켓 최초 연결 시 커넥션 카운터가 증가해야 한다")
	void handleWebSocketConnectListener_Success() {
		// given
		SessionConnectedEvent event = createConnectEvent("session-1");

		// when
		webSocketEventListener.handleWebSocketConnectListener(event);

		// then
		double count = meterRegistry.get("mopl.websocket.connection")
			.tag("status", "connected")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("멱등성: 동일한 세션 ID로 연결 이벤트가 중복 발생해도 카운터는 한 번만 증가한다")
	void handleWebSocketConnectListener_Idempotent() {
		// given
		SessionConnectedEvent event1 = createConnectEvent("session-1");
		SessionConnectedEvent event2 = createConnectEvent("session-1");

		// when
		webSocketEventListener.handleWebSocketConnectListener(event1);
		webSocketEventListener.handleWebSocketConnectListener(event2);

		// then
		double count = meterRegistry.get("mopl.websocket.connection")
			.tag("status", "connected")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("성공: 웹소켓 연결 해제 시 디스커넥트 카운터가 증가해야 한다")
	void handleWebSocketDisconnectListener_Success() {
		// given
		webSocketEventListener.handleWebSocketConnectListener(createConnectEvent("session-1")); // 사전 연결 처리
		SessionDisconnectEvent event = createDisconnectEvent("session-1");

		// when
		webSocketEventListener.handleWebSocketDisconnectListener(event);

		// then
		double count = meterRegistry.get("mopl.websocket.connection")
			.tag("status", "disconnected")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("멱등성: 동일한 세션 ID로 해제 이벤트가 중복 발생해도 카운터는 한 번만 증가한다")
	void handleWebSocketDisconnectListener_Idempotent() {
		// given
		webSocketEventListener.handleWebSocketConnectListener(createConnectEvent("session-1"));

		SessionDisconnectEvent event1 = createDisconnectEvent("session-1");
		SessionDisconnectEvent event2 = createDisconnectEvent("session-1");

		// when
		webSocketEventListener.handleWebSocketDisconnectListener(event1);
		webSocketEventListener.handleWebSocketDisconnectListener(event2);

		// then
		double count = meterRegistry.get("mopl.websocket.connection")
			.tag("status", "disconnected")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("실패방어: sessionId가 null인 이벤트가 수신되면 예외 없이 무시된다")
	void handleWebSocketListener_NullSessionId() {
		// given
		SessionConnectedEvent connectEvent = createConnectEvent(null);
		SessionDisconnectEvent disconnectEvent = createDisconnectEvent(null);

		// when & then
		webSocketEventListener.handleWebSocketConnectListener(connectEvent);
		webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

		// 메트릭이 수집되지 않았음을 검증
		boolean hasConnectedMetric = !meterRegistry.find("mopl.websocket.connection")
			.tag("status", "connected")
			.counters()
			.isEmpty();
		boolean hasDisconnectedMetric = !meterRegistry.find("mopl.websocket.connection")
			.tag("status", "disconnected")
			.counters()
			.isEmpty();

		assertThat(hasConnectedMetric).isFalse();
		assertThat(hasDisconnectedMetric).isFalse();
	}

	// 공통 메서드: 웹소켓 연결
	private SessionConnectedEvent createConnectEvent(String sessionId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		if (sessionId != null) {
			accessor.setSessionId(sessionId);
		}
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		return new SessionConnectedEvent(this, message, null);
	}

	// 공통 메서드: 웹소켓 연결 해제
	private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
		if (sessionId == null) {
			SessionDisconnectEvent mockEvent = mock(SessionDisconnectEvent.class);
			lenient().when(mockEvent.getSessionId()).thenReturn(null);
			
			return mockEvent;
		}

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		accessor.setSessionId(sessionId);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
	}
}
