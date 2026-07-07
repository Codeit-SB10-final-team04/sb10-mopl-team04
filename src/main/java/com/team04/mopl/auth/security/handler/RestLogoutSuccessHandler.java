package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그아웃 성공 응답을 처리하는 핸들러
 *
 * Refresh Token 쿠키를 만료시키고, 응답 body 없이 204 No Content를 반환
 */
@Component
@RequiredArgsConstructor
public class RestLogoutSuccessHandler implements LogoutSuccessHandler {

	private final RefreshTokenCookieWriter refreshTokenCookieWriter;

	// Refresh Token 쿠키를 만료시키고 204 No Content로 응답
	@Override
	public void onLogoutSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException {
		refreshTokenCookieWriter.expire(response);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
}
