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
import com.team04.mopl.watching.store.WatchingSessionStore;

import io.micrometer.core.instrument.MeterRegistry;
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
	private final WatchingSessionStore watchingSessionStore;
	private final MeterRegistry meterRegistry;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}

		try {
			// CONNECT 시 JWT 인증 수행
			String accessToken = resolveAccessToken(accessor);
			JwtAuthenticationClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
			validateAuthSession(claims);

			// 인증된 사용자 정보를 WebSocket 세션에 바인딩
			Principal principal = createAuthentication(claims);
			accessor.setUser(principal);

			// 세션 스토어에 등록
			String sessionId = accessor.getSessionId();
			if (sessionId != null) {
				watchingSessionStore.registerSession(sessionId, claims.userId());
			}

			log.debug("WebSocket 연결 인증 성공: userId={}", claims.userId());

			// 커스텀 메트릭 추가: STOMP 인증 성공
			meterRegistry.counter(
				"mopl.stomp.authentication",
				"result", "success"
			).increment();

			return message;

		} catch (Exception e) {
			// 커스텀 메트릭 추가: STOMP 인증 실패
			meterRegistry.counter(
				"mopl.stomp.authentication",
				"result", "failure"
			).increment();

			// 커스텀 메트릭 추가: STOMP 인증 실패 사유별 횟수
			String reason = extractFailReason(e);
			meterRegistry.counter(
				"mopl.stomp.authentication.failure",
				"reason", reason
			).increment();

			throw e;
		}
	}

	// 예외 사유 추출 메서드
	private String extractFailReason(Exception e) {
		if (e instanceof AuthException authException) {
			AuthErrorCode errorCode = (AuthErrorCode)authException.getErrorCode();

			if (errorCode == AuthErrorCode.AUTH_SESSION_INVALID) {
				return "invalid_session";
			} else if (errorCode == AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN) {
				return "invalid_token";
			}
		}
		return "unknown";
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