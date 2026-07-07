package com.team04.mopl.auth.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.team04.mopl.auth.entity.AuthSession;

public interface AuthSessionStore {

	// 로그인 성공 시 기존 세션을 제거하고 새 세션을 저장
	AuthSession replace(
		UUID userId,
		UUID sessionId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant issuedAt
	);

	// 사용자 활성 인증 세션 조회
	Optional<AuthSession> findByUserId(UUID userId);

	// 사용자(userId + sessionId 조합)가 현재 활성 세션인지 확인
	boolean isActive(UUID userId, UUID sessionId);

	// refresh token hash 기준 인증 세션 조회
	Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

	// refresh token 재발급 시 인증 세션의 refresh token과 만료시간을 갱신
	Optional<AuthSession> refresh(
		UUID userId,
		String currentRefreshTokenHash,
		String newRefreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant refreshedAt
	);

	// 사용자 인증 세션 삭제 (계정 잠금, 권한 변경, 관리자 강제 로그아웃 시 사용)
	void deleteByUserId(UUID userId);

	// 특정 인증 세션 삭제 (로그아웃 시 사용)
	void delete(UUID userId, UUID sessionId);
}
