package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
	* 로그인 실패 시 JSON 에러 응답을 반환하는 핸들러
	*
	* 인증 과정에서 발생한 예외를 커스텀 예외로 변환하고
	* AuthResponseWriter를 통해 ErrorResponse 형식으로 응답
 */
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

	private final AuthResponseWriter authResponseWriter;

	// 로그인 실패 시 ErrorResponse로 변환해 응답
	@Override
	public void onAuthenticationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		AuthErrorCode errorCode = resolveErrorCode(exception);
		AuthException authException = new AuthException(errorCode, exception);

		authResponseWriter.writeError(response, authException);
	}

	private AuthErrorCode resolveErrorCode(AuthenticationException exception) {
		if (exception instanceof LockedException) {
			return AuthErrorCode.LOCKED_ACCOUNT;
		}

		return AuthErrorCode.INVALID_CREDENTIALS;
	}
}
