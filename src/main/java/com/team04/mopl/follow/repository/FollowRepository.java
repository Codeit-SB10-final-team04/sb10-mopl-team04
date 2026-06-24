package com.team04.mopl.follow.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.team04.mopl.follow.entity.Follow;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

	// 유효성 검증: 중복 검사
	boolean existsByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);
}
