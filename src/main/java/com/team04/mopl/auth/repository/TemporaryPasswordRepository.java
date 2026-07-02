package com.team04.mopl.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.auth.entity.TemporaryPassword;

public interface TemporaryPasswordRepository extends JpaRepository<TemporaryPassword, UUID> {

	// 사용자 id 기준 임시 비밀번호 조회
	Optional<TemporaryPassword> findByUser_Id(UUID userId);

	// 사용자 id 기준 임시 비밀번호 삭제
	void deleteByUser_Id(UUID userId);
}
