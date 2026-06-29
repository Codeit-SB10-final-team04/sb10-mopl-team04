package com.team04.mopl.auth.security.cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.jwt.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Refresh Token을 HTTP 응답 쿠키로 내려주는 컴포넌트
 *
 * - 로그인 성공 시 생성된 Refresh Token 원문을 HttpOnly 쿠키에 담아 클라이언트에 전달
 * - 쿠키 이름, Secure 여부, SameSite 정책, 만료 시간은 JwtProperties 설정값을 사용
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieWriter {

	private final JwtProperties jwtProperties;

	// 로그인 성공 시 refresh token을 Set-Cookie 헤더에 추가
	public void write(HttpServletResponse response, String refreshToken) {
		ResponseCookie cookie = ResponseCookie.from(
				jwtProperties.refreshTokenCookieName(),
				refreshToken
			)
			.httpOnly(true)
			.secure(jwtProperties.refreshTokenCookieSecure())
			.path("/")
			.maxAge(jwtProperties.refreshTokenExpiration())
			.sameSite(jwtProperties.refreshTokenCookieSameSite())
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
