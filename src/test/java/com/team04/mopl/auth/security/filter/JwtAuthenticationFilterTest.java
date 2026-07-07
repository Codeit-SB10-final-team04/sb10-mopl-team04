package com.team04.mopl.auth.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

import jakarta.servlet.FilterChain;

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
	@DisplayName("POST 로그인 요청이면 JWT 필터를 적용하지 않는다")
	void doFilterInternal_passesRequest_whenRequestIsPostSignInPath() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/sign-in");
		request.setServletPath("/api/auth/sign-in");
		request.addHeader("Authorization", "Bearer invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(filterChain.getRequest()).isSameAs(request);
		verify(jwtTokenProvider, never()).parseAccessToken(any());
	}

	@Test
	@DisplayName("POST 로그아웃 요청이면 JWT 필터를 적용하지 않는다")
	void doFilterInternal_passesRequest_whenRequestIsPostSignOutPath() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/sign-out");
		request.setServletPath("/api/auth/sign-out");
		request.addHeader("Authorization", "Bearer invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(filterChain.getRequest()).isSameAs(request);
		verify(jwtTokenProvider, never()).parseAccessToken(any());
	}

	@Test
	@DisplayName("로그아웃 경로라도 POST가 아니면 JWT 필터를 적용한다")
	void doFilterInternal_savesAuthentication_whenSignOutPathMethodIsNotPost() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "valid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/sign-out");
		request.setServletPath("/api/auth/sign-out");
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
	@DisplayName("POST /api/auth/refresh 요청은 JWT 인증 필터를 적용하지 않는다")
	void doFilter_skipJwtAuthentication_whenRefreshRequest() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest(
			HttpMethod.POST.name(),
			"/api/auth/refresh"
		);
		request.setServletPath("/api/auth/refresh");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = mock(FilterChain.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(filterChain).doFilter(request, response);
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
		verifyNoInteractions(authResponseWriter);
	}

	@Test
	@DisplayName("GET /api/auth/csrf-token 요청은 JWT 인증 필터를 적용하지 않는다")
	void doFilter_passesRequest_whenRequestIsGetCsrfTokenPath() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest(
			HttpMethod.GET.name(),
			"/api/auth/csrf-token"
		);
		request.setServletPath("/api/auth/csrf-token");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		assertThat(filterChain.getRequest()).isSameAs(request);
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
		verifyNoInteractions(authResponseWriter);
	}

	@Test
	@DisplayName("POST /api/auth/reset-password 요청은 JWT 인증 필터를 적용하지 않는다")
	void doFilter_passesRequest_whenRequestIsPostResetPasswordPath() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest(
			HttpMethod.POST.name(),
			"/api/auth/reset-password"
		);
		request.setServletPath("/api/auth/reset-password");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = mock(FilterChain.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(filterChain).doFilter(request, response);
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
		verifyNoInteractions(authResponseWriter);
	}

	@Test
	@DisplayName("reset-password 경로라도 POST가 아니면 JWT 필터를 적용한다")
	void doFilter_writesError_whenResetPasswordPathMethodIsNotPost() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest(
			HttpMethod.GET.name(),
			"/api/auth/reset-password"
		);
		request.setServletPath("/api/auth/reset-password");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Token invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = mock(FilterChain.class);

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());
		verify(filterChain, never()).doFilter(request, response);
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("Bearer 형식이 아니면 인증 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenAuthorizationHeaderIsNotBearerType() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Token invalid-token");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());
		verify(jwtTokenProvider, never()).parseAccessToken(any());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
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

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());
		verify(jwtTokenProvider, never()).parseAccessToken(any());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
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
	@DisplayName("로그아웃으로 세션이 삭제된 Access Token이면 인증 에러 응답을 반환한다")
	void doFilterInternal_writesError_whenLoggedOutAccessTokenUsed() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "logged-out-access-token";

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

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_SESSION_INVALID);
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
			.thenThrow(new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN));

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		jwtAuthenticationFilter.doFilter(request, response, filterChain);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}