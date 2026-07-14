package com.team04.mopl.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.service.dto.AuthTokenIssueResult;
import com.team04.mopl.auth.session.AuthSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증 성공 사용자를 위한 access token, refresh token, 인증 세션을 한 번에 발급하는 컴포넌트
 *
 * - 일반 로그인과 소셜 로그인 성공 핸들러가 동일한 토큰 발급 규칙을 사용하도록 공통화
 * - refresh token 원문은 응답 쿠키로만 전달하고, 서버에는 해시값과 만료 시각을 인증 세션으로 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final AuthSessionStore authSessionStore;

	// 인증 성공 사용자에게 access token과 refresh token을 발급하고 인증 세션 저장
	@Transactional
	public AuthTokenIssueResult issue(MoplUserDetails userDetails) {
		log.info("[AuthTokenIssuer] 토큰 발급 시작 - userId={}", userDetails.getUserId());

		// 토큰 발급 기준 시각 생성
		Instant issuedAt = Instant.now();

		// access token에 담을 인증 세션 식별자 생성
		UUID sessionId = UUID.randomUUID();

		// access token과 refresh token 만료 시각 계산
		Instant accessExpiresAt = jwtTokenProvider.calculateAccessExpiresAt(issuedAt);
		Instant refreshExpiresAt = jwtTokenProvider.calculateRefreshExpiresAt(issuedAt);

		// access token 생성
		String accessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			sessionId,
			issuedAt,
			accessExpiresAt
		);

		// refresh token 원문 생성
		String refreshToken = refreshTokenGenerator.generate();

		// 서버 저장용 refresh token 해시 생성
		String refreshTokenHash = tokenHasher.hash(refreshToken);

		// 사용자 기준 기존 인증 세션 교체 저장
		authSessionStore.replace(
			userDetails.getUserId(),
			sessionId,
			refreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			issuedAt
		);

		log.info("[AuthTokenIssuer] 토큰 발급 완료 - userId={}, sessionId={}", userDetails.getUserId(), sessionId);

		// 응답 body용 JWT DTO와 쿠키 저장용 refresh token 반환
		return new AuthTokenIssueResult(
			new JwtDto(userDetails.toUserDto(), accessToken),
			refreshToken
		);
	}
}
