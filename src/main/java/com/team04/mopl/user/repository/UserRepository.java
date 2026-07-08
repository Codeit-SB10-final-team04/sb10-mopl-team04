package com.team04.mopl.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.qdsl.UserQdslRepository;

public interface UserRepository extends JpaRepository<User, UUID>, UserQdslRepository {
	// 이메일 중복 확인 시 사용
	boolean existsByEmail(String email);

	// 로그인 시 이메일로 사용자 조회
	Optional<User> findByEmail(String email);

	// lock 되지 않은 사용자 조회
	Optional<User> findByIdAndLockedFalse(UUID id);

	List<User> findAllByIdInAndLockedFalse(Set<UUID> ids);
}
