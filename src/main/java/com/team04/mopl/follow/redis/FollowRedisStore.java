package com.team04.mopl.follow.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRedisStore {

	private static final String FOLLOWING_KEY = "follow:following:%s";        // 내가 팔로우하는 사람들
	private static final String FOLLOWERS_KEY = "follow:followers:%s";        // 나를 팔로우하는 사람들

	private static final String EMPTY_FOLLOWERS_KEY = "follow:followers:empty:%s";

	// 원자적 업데이트: 자바 스크립트를 하나의 명령어로 취급
	private static final String ADD_FOLLOW_SCRIPT = """
		    local isNew = redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
		    if isNew == 1 then
		        redis.call('ZADD', KEYS[2], ARGV[1], ARGV[3])
		        return 1
		    end
		    return 0
		""";

	// 원자적 삭제
	private static final String REMOVE_FOLLOW_SCRIPT = """
		    redis.call('ZREM', KEYS[1], ARGV[1])
		    redis.call('ZREM', KEYS[2], ARGV[2])
		    return 1
		""";

	private final StringRedisTemplate stringRedisTemplate;

	// 팔로우 생성 (추가)
	public boolean addFollow(UUID followerId, UUID followeeId) {
		String followingKey = String.format(FOLLOWING_KEY, followerId);
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		long timestamp = System.currentTimeMillis();

		// 스크립트 실행
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_FOLLOW_SCRIPT, Long.class);
		Long result = stringRedisTemplate.execute(
			script,
			List.of(followingKey, followersKey),
			String.valueOf(timestamp),
			followeeId.toString(),
			followerId.toString()
		);

		return result != null && result == 1L;
	}

	// 사용자의 특정 사용자 팔로우 여부 조회
	public boolean isFollowing(UUID followerId, UUID followeeId) {
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		// 값이 존재하면 시간 반환, 존재하지 않으면 null 반환
		return stringRedisTemplate.opsForZSet().score(followingKey, followeeId.toString()) != null;
	}

	// 특정 유저의 팔로우 수 (팔로워) 조회
	public Long getFollowerCount(UUID followeeId) {
		// 팔로워가 0명인 경우
		String emptyKey = String.format(EMPTY_FOLLOWERS_KEY, followeeId);

		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(emptyKey))) {
			return 0L;
		}

		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		// Cache Miss 확인
		Boolean hasKey = stringRedisTemplate.hasKey(followersKey);
		if (Boolean.FALSE.equals(hasKey)) {
			return null;
		}

		// Set 전체 크기 반환
		Long count = stringRedisTemplate.opsForZSet().zCard(followersKey);

		return count != null
			? count
			: 0L;
	}

	// 특정 사용자의 팔로워 목록 백필
	public void initFollowers(UUID followeeId, Collection<UUID> followerIds) {

		if (followerIds == null || followerIds.isEmpty()) {
			String emptyKey = String.format(EMPTY_FOLLOWERS_KEY, followeeId);
			stringRedisTemplate.opsForValue().set(emptyKey, "1", Duration.ofMinutes(10));

			return;
		}

		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		long timestamp = System.currentTimeMillis();

		// Tuple set 타입으로 변환
		var tuples = followerIds.stream()
			.map(id -> ZSetOperations.TypedTuple.of(id.toString(), (double)timestamp))
			.collect(Collectors.toSet());

		stringRedisTemplate.opsForZSet().add(followersKey, tuples);
	}

	// 팔로우 삭제 (취소)
	public void removeFollow(UUID followeeId, UUID followerId) {
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		// 스크립트 실
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(REMOVE_FOLLOW_SCRIPT, Long.class);
		stringRedisTemplate.execute(
			script,
			List.of(followingKey, followersKey),
			followeeId.toString(),
			followerId.toString()
		);
	}
}
