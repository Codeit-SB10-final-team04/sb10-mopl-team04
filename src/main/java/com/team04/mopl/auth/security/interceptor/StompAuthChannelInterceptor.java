package com.team04.mopl.auth.security.interceptor;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.session.AuthSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String AUTHORIZATION_HEADER = "Authorization";

	private final JwtTokenProvider jwtTokenProvider;
	private final AuthSessionStore authSessionStore;

	// 클라이언트 → 서버로 메시지가 전달되기 전에 실행
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		// CONNECT 프레임이 아닌 경우 (SEND, SUBSCRIBE 등) 인증 검사 없이 통과
		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		// CONNECT 시 JWT 인증 수행
		String accessToken = resolveAccessToken(accessor);
		JwtAuthenticationClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
		validateAuthSession(claims);

		// 인증된 사용자 정보를 WebSocket 세션에 바인딩
		// 이후 @MessageMapping 핸들러에서 Principal로 접근 가능
		Principal principal = createAuthentication(claims);
		accessor.setUser(principal);

		log.debug("WebSocket 연결 인증 성공: userId={}", claims.userId());

		return message;
	}

	// STOMP CONNECT 프레임의 Authorization 헤더에서 Bearer 토큰 추출
	private String resolveAccessToken(StompHeaderAccessor accessor) {
		String authorizationHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

		if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		}

		String accessToken = authorizationHeader.substring(BEARER_PREFIX.length());

		if (accessToken.isBlank()) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		}

		return accessToken;
	}

	// JWT의 sessionId가 서버의 활성 세션과 일치하는지 검증
	private void validateAuthSession(JwtAuthenticationClaims claims) {
		boolean active = authSessionStore.isActive(claims.userId(), claims.sessionId());

		if (!active) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_INVALID);
		}
	}

	// JWT 클레임 기반으로 Spring Security 인증 객체 생성
	private Principal createAuthentication(JwtAuthenticationClaims claims) {
		MoplUserDetails principal = MoplUserDetails.authenticated(
			claims.userId(),
			claims.email(),
			claims.role()
		);

		return new UsernamePasswordAuthenticationToken(
			principal,
			null,
			principal.getAuthorities()
		);
	}
}
