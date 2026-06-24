package com.team04.mopl.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
	// 이메일 중복 확인 시 사용
	boolean existsByEmail(String email);
}
