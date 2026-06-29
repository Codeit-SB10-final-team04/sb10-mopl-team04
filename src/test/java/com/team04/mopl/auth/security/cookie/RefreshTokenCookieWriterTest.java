package com.team04.mopl.auth.security.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.team04.mopl.auth.security.jwt.JwtProperties;

class RefreshTokenCookieWriterTest {

	private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes";

	@Test
	@DisplayName("Refresh Token을 HttpOnly 쿠키로 응답에 추가한다")
	void write_addsRefreshTokenCookie_whenRefreshTokenProvided() {
		// given
		JwtProperties jwtProperties = new JwtProperties(
			SECRET,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			false,
			"Lax"
		);

		RefreshTokenCookieWriter cookieWriter = new RefreshTokenCookieWriter(jwtProperties);
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		cookieWriter.write(response, "refresh-token");

		// then
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).contains("REFRESH_TOKEN=refresh-token");
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("Path=/");
		assertThat(setCookie).contains("Max-Age=1209600");
		assertThat(setCookie).contains("SameSite=Lax");
		assertThat(setCookie).doesNotContain("Secure");
	}

	@Test
	@DisplayName("Secure 설정이 true이면 Secure 쿠키로 응답에 추가한다")
	void write_addsSecureCookie_whenSecurePropertyIsTrue() {
		// given
		JwtProperties jwtProperties = new JwtProperties(
			SECRET,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			true,
			"Lax"
		);

		RefreshTokenCookieWriter cookieWriter = new RefreshTokenCookieWriter(jwtProperties);
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		cookieWriter.write(response, "refresh-token");

		// then
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).contains("Secure");
	}

	@Test
	@DisplayName("Refresh Token 쿠키를 즉시 만료한다")
	void expire_expiresRefreshTokenCookie_whenLogoutSucceeds() {
		// given
		JwtProperties jwtProperties = new JwtProperties(
			SECRET,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			false,
			"Lax"
		);

		RefreshTokenCookieWriter cookieWriter = new RefreshTokenCookieWriter(jwtProperties);
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		cookieWriter.expire(response);

		// then
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).contains("REFRESH_TOKEN=");
		assertThat(setCookie).contains("Max-Age=0");
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("Path=/");
		assertThat(setCookie).contains("SameSite=Lax");
	}
}