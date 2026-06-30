package com.team04.mopl.auth.security.filter;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.session.AuthSessionStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization 헤더의 Bearer Access Token을 검증하는 인증 필터
 *
 * - Access Token의 서명, 만료 시간, 필수 Claim을 검증한 뒤,
 * 	 서버에 저장된 인증 세션과 JWT의 sessionId가 일치하는지 확인
 * - 검증에 성공하면 SecurityContext에 인증 객체 저장
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final AuthSessionStore authSessionStore;
	private final AuthResponseWriter authResponseWriter;

	// JWT Access Token 검증 및 인증 세션 확인 후 SecurityContext에 인증 객체 저장
	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			String accessToken = resolveAccessToken(request);

			if (accessToken == null) {
				filterChain.doFilter(request, response);

				return;
			}

			JwtAuthenticationClaims claims = jwtTokenProvider.parseAccessToken(accessToken);
			validateAuthSession(claims);
			saveAuthentication(request, claims);

			filterChain.doFilter(request, response);
		} catch (AuthException exception) {
			SecurityContextHolder.clearContext();
			authResponseWriter.writeError(response, exception);
		}
	}

	// 로그인/로그아웃/토큰 재발급 요청은 JWT 인증 필터를 적용하지 않도록 제외
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return isPost(request, "/api/auth/sign-in")
			|| isPost(request, "/api/auth/sign-out")
			|| isPost(request, "/api/auth/refresh");
	}

	// Authorization 헤더에서 Bearer Access Token 추출
	private String resolveAccessToken(HttpServletRequest request) {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			return null;
		}

		if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		}

		String accessToken = authorizationHeader.substring(BEARER_PREFIX.length());

		if (accessToken.isBlank()) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		}

		return accessToken;
	}

	// JWT의 sessionId 가 서버의 인증 세션과 일치하는지 검증
	private void validateAuthSession(JwtAuthenticationClaims claims) {
		boolean active = authSessionStore.isActive(
			claims.userId(),
			claims.sessionId()
		);

		if (!active) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_INVALID);
		}
	}

	// JWT claim을 기반으로 인증 객체 생성 및 SecurityContext에 저장
	private void saveAuthentication(HttpServletRequest request, JwtAuthenticationClaims claims) {
		MoplUserDetails principal = MoplUserDetails.authenticated(
			claims.userId(),
			claims.email(),
			claims.role()
		);

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			principal,
			null,
			principal.getAuthorities()
		);

		authentication.setDetails(
			new WebAuthenticationDetailsSource().buildDetails(request)
		);

		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	// 요청 경로와 HTTP Method가 일치하는지 확인
	private boolean isPost(HttpServletRequest request, String path) {
		return path.equals(request.getServletPath())
			&& HttpMethod.POST.matches(request.getMethod());
	}
}