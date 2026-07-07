package com.team04.mopl.auth.security.handler;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.auth.dto.response.JwtDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 성공 시 실행되는 핸들러
 *
 * - access token은 JSON 응답 body로 내려줌
 * - refresh token은 쿠키로 내려줌
 * - 서버에는 refresh token hash와 sessionId를 저장
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final RefreshTokenCookieWriter refreshTokenCookieWriter;
	private final AuthSessionStore authSessionStore;
	private final AuthResponseWriter authResponseWriter;

	// 로그인 성공 후 토큰을 발급하고 인증 세션을 저장한 뒤 로그인 응답 반환
	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException {
		MoplUserDetails userDetails = (MoplUserDetails) authentication.getPrincipal();

		Instant issuedAt = Instant.now();
		UUID sessionId = UUID.randomUUID();

		Instant accessExpiresAt = jwtTokenProvider.calculateAccessExpiresAt(issuedAt);
		Instant refreshExpiresAt = jwtTokenProvider.calculateRefreshExpiresAt(issuedAt);

		String accessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			sessionId,
			issuedAt,
			accessExpiresAt
		);

		String refreshToken = refreshTokenGenerator.generate();
		String refreshTokenHash = tokenHasher.hash(refreshToken);

		authSessionStore.replace(
			userDetails.getUserId(),
			sessionId,
			refreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			issuedAt
		);

		refreshTokenCookieWriter.write(response, refreshToken);

		JwtDto jwtDto = new JwtDto(
			userDetails.toUserDto(),
			accessToken
		);

		authResponseWriter.writeJson(response, HttpStatus.OK, jwtDto);
	}
}
