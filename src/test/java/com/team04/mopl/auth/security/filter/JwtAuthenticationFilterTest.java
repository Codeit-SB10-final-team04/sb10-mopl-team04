package com.team04.mopl.auth.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.UserRole;

class JwtAuthenticationFilterTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);

	private final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
		jwtTokenProvider,
		authSessionStore,
		authResponseWriter
	);

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Authorization 헤더가 없으면 인증 처리 없이 다음 필터로 넘긴다")
	void doFilterInternal_passesRequest_whenAuthorizationHeaderIsMissing() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(filterChain.getRequest()).isSameAs(request);
		verify(jwtTokenProvider, never()).parseAccessToken(any());
	}

	@Test
	@DisplayName("로그인 요청이면 JWT 필터를 적용하지 않는다")
	void doFilterInternal_passesRequest_whenRequestIsSignInPath() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/sign-in");
		request.setServletPath("/api/auth/sign-in");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(filterChain.getRequest()).isSameAs(request);
		verify(jwtTokenProvider, never()).parseAccessToken(any());
	}

	@Test
	@DisplayName("로그인 경로라도 POST가 아니면 JWT 필터를 적용한다")
	void doFilterInternal_savesAuthentication_whenSignInPathMethodIsNotPost() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "valid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/sign-in");
		request.setServletPath("/api/auth/sign-in");
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		when(jwtTokenProvider.parseAccessToken(accessToken)).thenReturn(claims);
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(true);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		verify(jwtTokenProvider).parseAccessToken(accessToken);
	}

	@Test
	@DisplayName("Bearer 형식이 아니면 인증 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenAuthorizationHeaderIsNotBearerType() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Token invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), any(AuthException.class));
		verify(jwtTokenProvider, never()).parseAccessToken(any());
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	@DisplayName("Bearer 뒤에 토큰이 없으면 인증 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenBearerTokenIsBlank() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer ");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), any(AuthException.class));
		verify(jwtTokenProvider, never()).parseAccessToken(any());
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	@DisplayName("유효한 Access Token과 활성 세션이면 인증 정보를 저장한다")
	void doFilterInternal_savesAuthentication_whenAccessTokenAndSessionAreValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "valid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		when(jwtTokenProvider.parseAccessToken(accessToken)).thenReturn(claims);
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(true);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

		MoplUserDetails principal = (MoplUserDetails) SecurityContextHolder
			.getContext()
			.getAuthentication()
			.getPrincipal();

		assertThat(principal.getUserId()).isEqualTo(userId);
		assertThat(principal.getEmail()).isEqualTo("test@test.com");
		assertThat(principal.getRole()).isEqualTo(UserRole.USER);
		assertThat(filterChain.getRequest()).isSameAs(request);
	}

	@Test
	@DisplayName("서버 인증 세션이 비활성 상태이면 인증 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenAuthSessionIsInactive() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "valid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		when(jwtTokenProvider.parseAccessToken(accessToken)).thenReturn(claims);
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(false);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), any(AuthException.class));
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	@DisplayName("Access Token 파싱 중 인증 예외가 발생하면 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenAccessTokenParsingFails() throws Exception {
		// given
		String accessToken = "invalid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		when(jwtTokenProvider.parseAccessToken(accessToken))
			.thenThrow(new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN));

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), any(AuthException.class));
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}