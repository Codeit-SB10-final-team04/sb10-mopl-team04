package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.service.AuthTokenIssuer;
import com.team04.mopl.auth.service.dto.AuthTokenIssueResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 일반 이메일/비밀번호 로그인 성공 후 MOPL 인증 토큰을 응답하는 핸들러
 *
 * - access token은 JSON 응답 body로 내려줌
 * - refresh token은 쿠키로 내려줌
 * - 서버에는 refresh token hash와 sessionId를 저장
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final AuthTokenIssuer authTokenIssuer;
	private final RefreshTokenCookieWriter refreshTokenCookieWriter;
	private final AuthResponseWriter authResponseWriter;

	// 로그인 성공 후 토큰을 발급하고 인증 세션을 저장한 뒤 로그인 응답 반환
	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException {
		// 인증 완료된 MOPL 사용자 principal 추출
		MoplUserDetails userDetails = (MoplUserDetails)authentication.getPrincipal();

		// access token, refresh token, 인증 세션 발급
		AuthTokenIssueResult result = authTokenIssuer.issue(userDetails);

		// refresh token 쿠키 저장
		refreshTokenCookieWriter.write(response, result.refreshToken());

		// access token과 사용자 정보를 JSON body로 응답
		authResponseWriter.writeJson(response, HttpStatus.OK, result.jwtDto());
	}
}
