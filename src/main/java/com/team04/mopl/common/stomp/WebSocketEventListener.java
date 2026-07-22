package com.team04.mopl.common.stomp;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

	private final MeterRegistry meterRegistry;

	private AtomicInteger activeConnections;

	@PostConstruct
	public void init() {
		// 커스텀 메트릭 추가: 현재 활성된 세션 개수
		activeConnections = meterRegistry.gauge(
			"mopl.websocket.connections",
			new AtomicInteger(0)
		);
	}

	// 웹소켓 연결
	@EventListener
	public void handleWebSocketConnectListener(SessionConnectedEvent sessionConnectedEvent) {
		activeConnections.incrementAndGet();

		meterRegistry.counter(
			"mopl.websocket.connection",
			"status", "connected"
		).increment();

		log.debug("[WEBSOCKET_CONNECT] 웹소켓 연결 성공: 현재 활성화 된 웹소켓 개수={}",
			activeConnections.get());
	}

	// 웹소켓 연결 해제
	@EventListener
	public void handleWebSocketDisconnectListener(SessionDisconnectEvent sessionDisconnectEvent) {
		activeConnections.decrementAndGet();

		meterRegistry.counter(
			"mopl.websocket.connection",
			"status", "disconnected"
		).increment();

		log.debug("[WEBSOCKET_CONNECT] 웹소켓 연결 해제 성공: 현재 활성화 된 웹소켓 개수={}",
			activeConnections.get());
	}

	// 웹소켓 구독 성공
	@EventListener
	public void handleSubscribeEvent(SessionSubscribeEvent sessionSubscribeEvent) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(sessionSubscribeEvent.getMessage());
		String destination = headerAccessor.getDestination();

		if (destination != null) {
			String destinationType = determineDestinationType(destination);

			// 커스텀 메트릭 추가: 구독 유형별 성공
			meterRegistry.counter(
				"mopl.stomp.subscription",
				"destination_type", destinationType,
				"result", "success"
			).increment();
		}
	}

	// 구독 유형 추출: 구독 URL 경로를 기반으로 구독 유형 추출
	private String determineDestinationType(String destination) {
		if (destination.contains("/direct-messages")) {
			return "dm";
		}
		if (destination.contains("/chat")) {
			return "content_chat";
		}
		if (destination.contains("/watching")) {
			return "watching_session";
		}
		return "other";
	}
}
