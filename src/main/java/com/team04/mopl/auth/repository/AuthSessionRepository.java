package com.team04.mopl.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.auth.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
	// 사용자 활성 인증 세션 조회 시 사용
	Optional<AuthSession> findByUser_Id(UUID userId);

	// 사용자가 현재 활성 세션인지 확인 시 사용
	boolean existsByUser_IdAndSessionId(UUID userId, UUID sessionId);

	// 사용자 인증 세션 삭제 시 사용 (계정 잠금, 권한 변경, 관리자 강제 로그아웃 시 사용)
    void deleteByUser_Id(UUID userId);

	// 특정 인증 세션 삭제 시 사용 (로그아웃 시 사용)
	void deleteByUser_IdAndSessionId(UUID userId, UUID sessionId);
}
