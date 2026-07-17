package com.team04.mopl.auth.session;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

public record AuthSessionData(
	UUID userId,
	UUID sessionId,
	String refreshTokenHash,
	Instant accessExpiresAt,
	Instant refreshExpiresAt,
	Instant lastRefreshedAt,
	Instant updatedAt
) {

	public AuthSessionData {
		// 세션을 식별하거나 검증하는 데 필요한 필수값이 모두 존재하는지 확인
		validateRequired(userId, sessionId, refreshTokenHash, accessExpiresAt, refreshExpiresAt, updatedAt);

		// access token과 refresh token의 만료 순서가 올바른지 확인
		validateExpiration(updatedAt, accessExpiresAt, refreshExpiresAt);
	}

	// 요청에 담긴 sessionId가 해당 세션의 식별자와 같은지 확인
	public boolean matchesSessionId(UUID candidateSessionId) {
		return sessionId.equals(candidateSessionId);
	}

	// 쿠키에서 받은 refresh token의 해시가 현재 세션에 저장된 해시와 같은지 확인
	public boolean matchesRefreshTokenHash(String candidateRefreshTokenHash) {
		return refreshTokenHash.equals(candidateRefreshTokenHash);
	}

	// refresh token이 만료 되었는지 확인
	public boolean isRefreshTokenExpired(Instant now) {
		// 호출자가 비교 기준 시각을 전달하지 않으면 예외 반환
		if (now == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}

		// 만료 시각과 같은 순간부터 만료된 것으로 처리
		return !now.isBefore(refreshExpiresAt);
	}

	// Redis Hash 필수값 검증
	private static void validateRequired(
		UUID userId,
		UUID sessionId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant updatedAt
	) {
		// 하나라도 누락되면 불완전 세션이므로 인증에 사용하지 않음
		if (userId == null
			|| sessionId == null
			|| refreshTokenHash == null
			|| refreshTokenHash.isBlank()
			|| accessExpiresAt == null
			|| refreshExpiresAt == null
			|| updatedAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	// 토큰 발급 시각, access token 만료, refresh token 만료 순서 검증
	private static void validateExpiration(
		Instant updatedAt,
		Instant accessExpiresAt,
		Instant refreshExpiresAt
	) {
		// updatedAt < accessExpiresAt < refreshExpiresAt 순서가 아니면 잘못된 세션
		if (!accessExpiresAt.isAfter(updatedAt) || !refreshExpiresAt.isAfter(accessExpiresAt)) {
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_EXPIRATION_INVALID);
		}
	}
}
