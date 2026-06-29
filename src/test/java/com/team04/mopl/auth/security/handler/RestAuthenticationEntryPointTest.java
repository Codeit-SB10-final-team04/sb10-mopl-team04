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

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

class RestAuthenticationEntryPointTest {

	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);

	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint = new RestAuthenticationEntryPoint(
		authResponseWriter
	);

	@Test
	@DisplayName("미인증 요청이면 UNAUTHORIZED 에러 응답을 반환한다")
	void commence_writesUnauthorizedError_whenAuthenticationIsMissing() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		BadCredentialsException exception = new BadCredentialsException("unauthorized");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		restAuthenticationEntryPoint.commence(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.UNAUTHORIZED);
	}
}