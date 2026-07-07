package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 인증은 되었지만 권한이 부족한 요청을 처리하는 핸들러
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final AuthResponseWriter authResponseWriter;

	// 권한 부족 예외를 403 JSON 응답으로 변환
	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		AccessDeniedException accessDeniedException
	) throws IOException {
		authResponseWriter.writeError(
			response,
			new AuthException(AuthErrorCode.AUTH_ACCESS_DENIED, accessDeniedException)
		);
	}
}
