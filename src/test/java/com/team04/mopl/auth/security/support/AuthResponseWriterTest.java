package com.team04.mopl.auth.security.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

class AuthResponseWriterTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AuthResponseWriter authResponseWriter = new AuthResponseWriter(objectMapper);

	@Test
	@DisplayName("일반 객체를 JSON 응답으로 작성한다")
	void writeJson_writesJsonResponse_whenBodyProvided() throws Exception {
		// given
		MockHttpServletResponse response = new MockHttpServletResponse();
		Map<String, String> body = Map.of("accessToken", "access-token");

		// when
		authResponseWriter.writeJson(response, HttpStatus.OK, body);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
		assertThat(response.getContentAsString()).contains("accessToken");
		assertThat(response.getContentAsString()).contains("access-token");
	}

	@Test
	@DisplayName("인증 예외를 ErrorResponse JSON 응답으로 작성한다")
	void writeError_writesErrorResponse_whenAuthExceptionProvided() throws Exception {
		// given
		MockHttpServletResponse response = new MockHttpServletResponse();
		AuthException exception = new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);

		// when
		authResponseWriter.writeError(response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN.getHttpStatus().value());
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
		assertThat(response.getContentAsString()).contains("AuthException");
		assertThat(response.getContentAsString()).contains(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN.getMessage());
		assertThat(response.getContentAsString()).contains("details");
	}
}