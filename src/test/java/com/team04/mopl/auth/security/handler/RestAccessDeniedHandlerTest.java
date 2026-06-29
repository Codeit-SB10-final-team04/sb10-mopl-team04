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
import org.springframework.security.access.AccessDeniedException;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.support.AuthResponseWriter;

class RestAccessDeniedHandlerTest {

	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);
	private final RestAccessDeniedHandler restAccessDeniedHandler = new RestAccessDeniedHandler(authResponseWriter);

	@Test
	@DisplayName("권한이 부족하면 ACCESS_DENIED 에러 응답을 반환한다")
	void handle_writesAccessDeniedError_whenAuthorityIsInsufficient() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AccessDeniedException exception = new AccessDeniedException("access denied");

		ArgumentCaptor<AuthException> exceptionCaptor = ArgumentCaptor.forClass(AuthException.class);

		// when
		restAccessDeniedHandler.handle(request, response, exception);

		// then
		verify(authResponseWriter).writeError(same(response), exceptionCaptor.capture());

		assertThat(exceptionCaptor.getValue())
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_ACCESS_DENIED);
	}
}