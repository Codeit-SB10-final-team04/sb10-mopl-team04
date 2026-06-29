package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.jwt.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;

class RestLogoutSuccessHandlerTest {

	private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes";

	private final JwtProperties jwtProperties = new JwtProperties(
		SECRET,
		"mopl",
		1800,
		1209600,
		"REFRESH_TOKEN",
		false,
		"Lax"
	);

	private final RefreshTokenCookieWriter refreshTokenCookieWriter = new RefreshTokenCookieWriter(jwtProperties);

	private final RestLogoutSuccessHandler restLogoutSuccessHandler = new RestLogoutSuccessHandler(
		refreshTokenCookieWriter
	);

	@Test
	@DisplayName("로그아웃 성공 시 Refresh Token 쿠키를 만료하고 204 No Content로 응답한다")
	void onLogoutSuccess_expiresRefreshTokenCookie_whenLogoutSucceeds() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		restLogoutSuccessHandler.onLogoutSuccess(request, response, null);

		// then
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(setCookie).contains("REFRESH_TOKEN=");
		assertThat(setCookie).contains("Max-Age=0");
		assertThat(setCookie).contains("Path=/");
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("SameSite=Lax");
	}
}
