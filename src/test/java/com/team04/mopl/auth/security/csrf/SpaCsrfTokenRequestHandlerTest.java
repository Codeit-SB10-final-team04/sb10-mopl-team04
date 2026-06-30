package com.team04.mopl.auth.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SpaCsrfTokenRequestHandlerTest {

	private final SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();

	@Test
	@DisplayName("CSRF 토큰을 쿠키로 내려주기 위해 토큰을 강제로 로드한다")
	void handle_loadsCsrfToken_whenCalled() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		CsrfToken csrfToken = new DefaultCsrfToken(
			"X-XSRF-TOKEN",
			"_csrf",
			"csrf-token"
		);

		AtomicBoolean loaded = new AtomicBoolean(false);

		Supplier<CsrfToken> csrfTokenSupplier = () -> {
			loaded.set(true);
			return csrfToken;
		};

		// when
		handler.handle(request, response, csrfTokenSupplier);

		// then
		assertThat(loaded).isTrue();
	}

	@Test
	@DisplayName("X-XSRF-TOKEN 헤더가 있으면 원본 CSRF 토큰 값을 반환한다")
	void resolveCsrfTokenValue_returnHeaderValue_whenHeaderExists() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();

		CsrfToken csrfToken = new DefaultCsrfToken(
			"X-XSRF-TOKEN",
			"_csrf",
			"csrf-token"
		);

		request.addHeader("X-XSRF-TOKEN", "csrf-token");

		// when
		String result = handler.resolveCsrfTokenValue(request, csrfToken);

		// then
		assertThat(result).isEqualTo("csrf-token");
	}

	@Test
	@DisplayName("X-XSRF-TOKEN 헤더가 없으면 CSRF 토큰 값을 반환하지 않는다")
	void resolveCsrfTokenValue_returnNull_whenHeaderDoesNotExist() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();

		CsrfToken csrfToken = new DefaultCsrfToken(
			"X-XSRF-TOKEN",
			"_csrf",
			"csrf-token"
		);

		// when
		String result = handler.resolveCsrfTokenValue(request, csrfToken);

		// then
		assertThat(result).isNull();
	}

	@Test
	@DisplayName("X-XSRF-TOKEN 헤더가 공백이면 CSRF 토큰 값을 반환하지 않는다")
	void resolveCsrfTokenValue_returnNull_whenHeaderIsBlank() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();

		CsrfToken csrfToken = new DefaultCsrfToken(
			"X-XSRF-TOKEN",
			"_csrf",
			"csrf-token"
		);

		request.addHeader("X-XSRF-TOKEN", "   ");

		// when
		String result = handler.resolveCsrfTokenValue(request, csrfToken);

		// then
		assertThat(result).isNull();
	}

	@Test
	@DisplayName("X-XSRF-TOKEN 헤더가 없고 _csrf 파라미터가 있으면 XOR 토큰 값을 해석한다")
	void resolveCsrfTokenValue_returnToken_whenXorParameterExists() {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		CsrfToken csrfToken = new DefaultCsrfToken(
			"X-XSRF-TOKEN",
			"_csrf",
			"csrf-token"
		);

		handler.handle(
			request,
			response,
			() -> csrfToken
		);

		CsrfToken maskedCsrfToken = (CsrfToken) request.getAttribute("_csrf");

		request.setParameter("_csrf", maskedCsrfToken.getToken());

		// when
		String result = handler.resolveCsrfTokenValue(request, csrfToken);

		// then
		assertThat(result).isEqualTo("csrf-token");
	}
}