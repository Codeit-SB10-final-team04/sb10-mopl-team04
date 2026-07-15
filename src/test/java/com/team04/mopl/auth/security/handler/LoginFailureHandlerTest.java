package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.common.exception.ErrorResponse;

class LoginFailureHandlerTest {

	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);
	private final LoginFailureHandler loginFailureHandler = new LoginFailureHandler(authResponseWriter);

	@Test
	@DisplayName("계정 잠금 예외이면 LOCKED_ACCOUNT 에러 응답을 반환한다")
	void onAuthenticationFailure_writesLockedAccountError_whenExceptionIsLockedException() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter("username", "test@test.com");
		request.addParameter("password", "password123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		LockedException exception = new LockedException("locked");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_LOCKED_ACCOUNT);
	}

	@Test
	@DisplayName("일반 로그인 실패이면 INVALID_CREDENTIALS 에러 응답을 반환한다")
	void onAuthenticationFailure_writesInvalidCredentialsError_whenExceptionIsNotLockedException() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter("username", "test@test.com");
		request.addParameter("password", "wrong-password");
		MockHttpServletResponse response = new MockHttpServletResponse();
		BadCredentialsException exception = new BadCredentialsException("bad credentials");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
	}

	@Test
	@DisplayName("인증 서비스 내부 오류이면 AUTHENTICATION_SERVICE_ERROR 에러 응답을 반환한다")
	void onAuthenticationFailure_writesAuthenticationServiceError_whenExceptionIsInternalServiceException()
		throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter("username", "test@test.com");
		request.addParameter("password", "password123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		InternalAuthenticationServiceException exception = new InternalAuthenticationServiceException("database error");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_SERVICE_ERROR);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("missingRequiredLoginParameterCases")
	@DisplayName("필수 로그인 파라미터가 누락되면 400 에러 응답을 반환한다")
	void onAuthenticationFailure_writesBadRequest_whenRequiredParameterIsMissing(
		String description,
		boolean includeUsername,
		boolean includePassword,
		Map<String, String> expectedDetails
	) throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (includeUsername) {
			request.addParameter("username", "test@test.com");
		}
		if (includePassword) {
			request.addParameter("password", "password123");
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		BadCredentialsException exception = new BadCredentialsException("bad credentials");

		ArgumentCaptor<ErrorResponse> errorResponseCaptor = ArgumentCaptor.forClass(ErrorResponse.class);

		// when
		loginFailureHandler.onAuthenticationFailure(request, response, exception);

		// then
		verify(authResponseWriter).writeJson(
			same(response),
			eq(HttpStatus.BAD_REQUEST),
			errorResponseCaptor.capture()
		);
		verify(authResponseWriter, never()).writeError(same(response), any(AuthException.class));

		assertThat(errorResponseCaptor.getValue().getDetails())
			.hasSize(expectedDetails.size())
			.containsAllEntriesOf(expectedDetails);
	}

	private static Stream<Arguments> missingRequiredLoginParameterCases() {
		return Stream.of(
			Arguments.of(
				"username 누락",
				false,
				true,
				Map.of("username", "필수 값입니다.")
			),
			Arguments.of(
				"password 누락",
				true,
				false,
				Map.of("password", "필수 값입니다.")
			),
			Arguments.of(
				"username/password 모두 누락",
				false,
				false,
				Map.of(
					"username", "필수 값입니다.",
					"password", "필수 값입니다."
				)
			)
		);
	}
}
