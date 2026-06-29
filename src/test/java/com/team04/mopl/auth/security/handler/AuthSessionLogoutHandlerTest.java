package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.UserRole;

class AuthSessionLogoutHandlerTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);

	private final AuthSessionLogoutHandler authSessionLogoutHandler = new AuthSessionLogoutHandler(
		jwtTokenProvider,
		authSessionStore
	);

	@Test
	@DisplayName("Bearer Access Token이 있으면 해당 인증 세션을 삭제한다")
	void logout_deletesAuthSession_whenBearerAccessTokenExists() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		when(jwtTokenProvider.parseAccessToken(accessToken)).thenReturn(claims);

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verify(authSessionStore).delete(userId, sessionId);
	}

	@Test
	@DisplayName("Authorization 헤더가 없으면 인증 세션 삭제를 시도하지 않는다")
	void logout_doesNothing_whenAuthorizationHeaderIsMissing() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("Bearer 형식이 아니면 인증 세션 삭제를 시도하지 않는다")
	void logout_doesNothing_whenAuthorizationHeaderIsNotBearerType() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Token access-token");

		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("Bearer 뒤에 토큰이 없으면 인증 세션 삭제를 시도하지 않는다")
	void logout_doesNothing_whenBearerTokenIsBlank() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer ");

		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verifyNoInteractions(jwtTokenProvider);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("Authorization scheme이 소문자 bearer여도 인증 세션을 삭제한다")
	void logout_deletesAuthSession_whenAuthorizationSchemeIsLowerCaseBearer() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		given(jwtTokenProvider.parseAccessToken(accessToken)).willReturn(claims);

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verify(jwtTokenProvider).parseAccessToken(accessToken);
		verify(authSessionStore).delete(userId, sessionId);
	}

	@Test
	@DisplayName("Bearer와 Access Token 사이에 공백이 여러 개 있어도 인증 세션을 삭제한다")
	void logout_deletesAuthSession_whenAuthorizationHeaderHasMultipleSpaces() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer    " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		given(jwtTokenProvider.parseAccessToken(accessToken)).willReturn(claims);

		// when
		authSessionLogoutHandler.logout(request, response, null);

		// then
		verify(jwtTokenProvider).parseAccessToken(accessToken);
		verify(authSessionStore).delete(userId, sessionId);
	}


	@Test
	@DisplayName("Access Token이 유효하지 않아도 예외를 전파하지 않는다")
	void logout_doesNotThrowException_whenAccessTokenIsInvalid() {
		// given
		String accessToken = "invalid-access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();

		when(jwtTokenProvider.parseAccessToken(accessToken))
			.thenThrow(new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN));

		// when & then
		assertThatCode(() -> authSessionLogoutHandler.logout(request, response, null))
			.doesNotThrowAnyException();

		verify(authSessionStore, never()).delete(any(), any());
	}

	@Test
	@DisplayName("인증 세션이 이미 삭제된 상태여도 delete 호출은 멱등하게 수행된다")
	void logout_deletesAuthSession_whenAuthSessionAlreadyDeleted() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String accessToken = "access-token";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + accessToken);

		MockHttpServletResponse response = new MockHttpServletResponse();

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		when(jwtTokenProvider.parseAccessToken(accessToken)).thenReturn(claims);

		// when & then
		assertThatCode(() -> authSessionLogoutHandler.logout(request, response, null))
			.doesNotThrowAnyException();

		verify(authSessionStore).delete(userId, sessionId);
	}
}