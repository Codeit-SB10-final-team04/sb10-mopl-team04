package com.team04.mopl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.team04.mopl.auth.security.interceptor.StompAuthChannelInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket + STOMP 메시징 설정
 *
 * <p>STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위에서 동작하는 메시징 프로토콜로,
 * 발행/구독(pub/sub) 패턴을 지원합니다.</p>
 *
 * <h3>메시지 흐름</h3>
 * <pre>
 *   클라이언트 → /pub/... → 서버(@MessageMapping) → /sub/... → 구독 중인 클라이언트들
 * </pre>
 *
 * <h3>연결 과정</h3>
 * <ol>
 *   <li>클라이언트가 /ws 엔드포인트로 WebSocket 연결 (HTTP Upgrade)</li>
 *   <li>STOMP CONNECT 프레임 전송 시 Authorization 헤더로 JWT 인증</li>
 *   <li>인증 성공 후 /sub/... 경로로 메시지 구독 가능</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker // STOMP 메시징 브로커 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

	// 메시지 브로커 경로 설정
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// /sub로 시작하는 경로를 구독하면 브로커가 해당 경로로 메시지를 전달
		// 예: 클라이언트가 /sub/contents/123/chat 을 구독하면, 해당 경로로 발행된 메시지를 수신
		// TODO: 다중 서버 환경에서는 인메모리 브로커로는 서버 간 메시지 전달이 불가능하므로
		//  외부 메시지 브로커(Redis Pub/Sub, RabbitMQ 등)로 교체 필요
		registry.enableSimpleBroker("/sub");

		// /pub로 시작하는 경로로 메시지를 보내면 서버의 @MessageMapping 핸들러가 처리
		registry.setApplicationDestinationPrefixes("/pub");
	}

	// WebSocket 연결 엔드포인트 등록
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")          // 클라이언트가 WebSocket 연결할 URL
			.setAllowedOriginPatterns("*")   // CORS 허용
			.withSockJS();                   // WebSocket 미지원 브라우저를 위한 SockJS 폴백
	}

	// 클라이언트 → 서버 메시지 채널에 인증 인터셉터 등록
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}
}
