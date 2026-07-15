package com.team04.mopl.follow.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team04.mopl.follow.entity.Follow;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

	// 유효성 검증: 중복 검사
	boolean existsByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

	// 단건 조회 (팔로위 Id, 팔로워 Id)
	Optional<Follow> findByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

	// 팔로우 대상(followee)의 팔로워 수 조회
	long countByFolloweeId(UUID followeeId);

	// 특정 사용자를 팔로우하는 팔로워들의 ID 목록 조회
	@Query(value = """
		SELECT fr.id
		FROM Follow AS f
		JOIN f.follower AS fr
		WHERE f.followee.id = :followeeId
			AND fr.locked = false
		""")
	Set<UUID> findFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);
}
