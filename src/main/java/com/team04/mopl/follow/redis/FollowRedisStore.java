package com.team04.mopl.follow.redis;

import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRedisStore {

	private static final String FOLLOWING_KEY = "follow:following:%s";        // 내가 팔로우하는 사람들
	private static final String FOLLOWERS_KEY = "follow:followers:%s";        // 나를 팔로우하는 사람들

	private final StringRedisTemplate stringRedisTemplate;

	// 팔로우 생성 (추가)
	public boolean addFollow(UUID followerId, UUID followeeId) {
		String followingKey = String.format(FOLLOWING_KEY, followerId);
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		long timestamp = System.currentTimeMillis();

		// 데이터가 새로 추가되면 true, 이미 존재하면 false 반환
		Boolean isAdded = stringRedisTemplate.opsForZSet().add(followingKey, followeeId.toString(), timestamp);

		if (Boolean.TRUE.equals(isAdded)) {
			// 내가 팔로우 성공했을 때만 상대방의 팔로워 목록에 추가
			stringRedisTemplate.opsForZSet().add(followersKey, followerId.toString(), timestamp);

			return true;
		}
		return false;
	}

	// 사용자의 특정 사용자 팔로우 여부 조회
	public boolean isFollowing(UUID followerId, UUID followeeId) {
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		// 값이 존재하면 시간 반환, 존재하지 않으면 null 반환
		return stringRedisTemplate.opsForZSet().score(followingKey, followeeId.toString()) != null;
	}

	// 특정 유저의 팔로우 수 (팔로워) 조회
	public Long getFollowerCount(UUID followeeId) {
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		// Set 전체 크기 반환
		Long count = stringRedisTemplate.opsForZSet().zCard(followersKey);

		return count != null
			? count
			: 0L;
	}

	// 팔로우 삭제 (취소)
	public void removeFollow(UUID followeeId, UUID followerId) {
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		stringRedisTemplate.opsForZSet().remove(followingKey, followeeId.toString());
		stringRedisTemplate.opsForZSet().remove(followersKey, followerId.toString());
	}
}
