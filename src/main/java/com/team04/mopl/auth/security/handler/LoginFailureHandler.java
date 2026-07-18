package com.team04.mopl.auth.security.handler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.common.exception.ErrorResponse;

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

	private static final String USERNAME_PARAMETER = "username";
	private static final String PASSWORD_PARAMETER = "password";

	private final AuthResponseWriter authResponseWriter;

	// 로그인 실패 시 ErrorResponse로 변환해 응답
	@Override
	public void onAuthenticationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		Map<String, String> missingParameters = resolveMissingRequiredLoginParameterDetails(request);

		// 필수 폼 파라미터 누락 시 400으로 응답
		if (!missingParameters.isEmpty()) {
			authResponseWriter.writeJson(
				response,
				HttpStatus.BAD_REQUEST,
				ErrorResponse.of(
					exception,
					"필수 로그인 파라미터가 누락되었습니다.",
					missingParameters
				)
			);

			return;
		}

		AuthErrorCode errorCode = resolveErrorCode(exception);
		AuthException authException = new AuthException(errorCode, exception);

		authResponseWriter.writeError(response, authException);
	}

	// 누락된 필드만 details에 담아 클라이언트가 보완할 값을 명확히 알 수 있게 함
	private Map<String, String> resolveMissingRequiredLoginParameterDetails(HttpServletRequest request) {
		Map<String, String> details = new LinkedHashMap<>();

		if (!StringUtils.hasText(request.getParameter(USERNAME_PARAMETER))) {
			details.put(USERNAME_PARAMETER, "필수 값입니다.");
		}

		if (!StringUtils.hasText(request.getParameter(PASSWORD_PARAMETER))) {
			details.put(PASSWORD_PARAMETER, "필수 값입니다.");
		}

		return details;
	}

	// 예외 타입에 맞는 인증 에러 코드를 결정
	private AuthErrorCode resolveErrorCode(AuthenticationException exception) {
		if (exception instanceof LockedException) {
			return AuthErrorCode.AUTH_LOCKED_ACCOUNT;
		}

		if (exception instanceof BadCredentialsException || exception instanceof UsernameNotFoundException) {
			return AuthErrorCode.AUTH_INVALID_CREDENTIALS;
		}

		if (exception instanceof AuthenticationServiceException) {
			return AuthErrorCode.AUTH_SERVICE_ERROR;
		}

		return AuthErrorCode.AUTH_SERVICE_ERROR;
	}
}
