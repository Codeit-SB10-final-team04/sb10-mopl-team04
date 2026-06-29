package com.team04.mopl.auth.security.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.common.exception.MoplException;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthResponseWriter {

	private final ObjectMapper objectMapper;

	// Security Filter 단계에서는 ControllerAdvice를 타지 않기 때문에 직접 JSON 응답을 작성
	public void writeError(HttpServletResponse response, MoplException exception) throws IOException {
		writeJson(
			response,
			exception.getErrorCode().getHttpStatus(),
			ErrorResponse.from(exception)
		);
	}

	// 로그인 성공/실패 응답 JSON 직렬화
	public void writeJson(
		HttpServletResponse response,
		HttpStatus httpStatus,
		Object body
	) throws IOException {
		response.setStatus(httpStatus.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		objectMapper.writeValue(response.getWriter(), body);
	}
}
