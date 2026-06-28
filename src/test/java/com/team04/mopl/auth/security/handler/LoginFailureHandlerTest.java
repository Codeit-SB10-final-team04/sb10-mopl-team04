package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

class LoginFailureHandlerTest {

	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);
	private final LoginFailureHandler loginFailureHandler = new LoginFailureHandler(authResponseWriter);

	@Test
	@DisplayName("계정 잠금 예외이면 LOCKED_ACCOUNT 에러 응답을 반환한다")
	void onAuthenticationFailure_writesLockedAccountError_whenExceptionIsLockedException() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		LockedException exception = new LockedException("locked");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.LOCKED_ACCOUNT);
	}

	@Test
	@DisplayName("일반 로그인 실패이면 INVALID_CREDENTIALS 에러 응답을 반환한다")
	void onAuthenticationFailure_writesInvalidCredentialsError_whenExceptionIsNotLockedException() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		BadCredentialsException exception = new BadCredentialsException("bad credentials");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
	}
}