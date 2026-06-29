package com.team04.mopl.auth.security.handler;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.session.AuthSessionStore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그아웃 요청 시 현재 Access Token에 연결된 인증 세션을 삭제하는 핸들러
 *
 * 로그아웃은 멱등하게 처리되어야 하므로 Access Token이 없거나 이미 세션이 삭제된 경우에도
 * 예외를 전파하지 않고 안전하게 종료
 */
@Component
@RequiredArgsConstructor
public class AuthSessionLogoutHandler implements LogoutHandler {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final AuthSessionStore authSessionStore;

	// Authorization 헤더의 Access Token에서 sessionId를 추출해 인증 세션을 삭제
	@Override
	public void logout(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) {
		resolveAccessToken(request)
			.ifPresent(this::deleteAuthSession);
	}

	// Access Token을 검증하고 해당 인증 세션을 삭제
	private void deleteAuthSession(String accessToken) {
		try {
			JwtAuthenticationClaims claims = jwtTokenProvider.parseAccessToken(accessToken);

			authSessionStore.delete(
				claims.userId(),
				claims.sessionId()
			);
		} catch (AuthException ignored) {
			// 로그아웃은 멱등 처리가 목적이므로 유효하지 않은 토큰은 세션 삭제 없이 무시
		}
	}

	// Authorization 헤더에서 Bearer Access Token을 추출합니다.
	private Optional<String> resolveAccessToken(HttpServletRequest request) {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			return Optional.empty();
		}

		String trimmedAuthorizationHeader = authorizationHeader.trim();

		if (!trimmedAuthorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return Optional.empty();
		}

		String accessToken = trimmedAuthorizationHeader.substring(BEARER_PREFIX.length()).trim();

		if (accessToken.isBlank()) {
			return Optional.empty();
		}

		return Optional.of(accessToken);
	}
}
