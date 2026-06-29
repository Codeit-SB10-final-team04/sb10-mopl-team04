package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 인증되지 않은 사용자의 보호 API 접근을 처리하는 핸들러
 *
 * Authorization 헤더가 없거나 인증 정보가 없는 상태에서 인증이 필요한 API에 접근하면
 * 401 Unauthorized ErrorResponse 반환
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final AuthResponseWriter authResponseWriter;

	// 미인증 요청 예외를 401 JSON 응답으로 변환
	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authException
	) throws IOException {
		authResponseWriter.writeError(
			response,
			new AuthException(AuthErrorCode.UNAUTHORIZED, authException)
		);
	}
}
