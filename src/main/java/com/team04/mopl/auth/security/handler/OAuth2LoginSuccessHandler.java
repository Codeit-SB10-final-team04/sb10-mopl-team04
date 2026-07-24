package com.team04.mopl.auth.security.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.oauth2.MoplOAuth2User;
import com.team04.mopl.auth.security.oauth2.OAuth2Properties;
import com.team04.mopl.auth.service.AuthTokenIssuer;
import com.team04.mopl.auth.service.dto.AuthTokenIssueResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 성공 후 MOPL 인증 토큰을 발급하고 프론트엔드 성공 화면으로 이동시키는 핸들러
 *
 * - Google OIDC와 Kakao OAuth2 로그인 모두 같은 성공 흐름을 사용
 * - Spring Security가 만든 소셜 인증 principal에서 MOPL 사용자 정보를 꺼내고,
 *   일반 로그인과 동일한 방식으로 refresh token 쿠키와 서버 인증 세션을 생성
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final AuthTokenIssuer authTokenIssuer;
	private final RefreshTokenCookieWriter refreshTokenCookieWriter;
	private final OAuth2Properties oauth2Properties;

	// 소셜 로그인 성공 후 refresh token을 쿠키에 저장하고 프론트엔드로 이동
	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException {
		// 소셜 인증 principal 추출
		OAuth2User oauth2User = (OAuth2User)authentication.getPrincipal();

		// MOPL 사용자 principal 변환
		MoplOAuth2User moplOAuth2User = (MoplOAuth2User)oauth2User;

		// access token, refresh token, 인증 세션 발급
		AuthTokenIssueResult result = authTokenIssuer.issue(moplOAuth2User.getUserDetails());

		// refresh token 쿠키 저장
		refreshTokenCookieWriter.write(response, result.refreshToken());

		// 프론트엔드 성공 화면 리다이렉트
		response.sendRedirect(oauth2Properties.getSuccessRedirectUri());
	}
}
