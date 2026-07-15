package com.team04.mopl.follow.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.enums.CacheState;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FollowRedisStore {

	private static final String FOLLOWING_KEY = "follow:following:%s";                // 내가 팔로우하는 사람들
	private static final String FOLLOWERS_KEY = "follow:followers:%s";                // 나를 팔로우하는 사람들
	private static final String EMPTY_FOLLOWERS_KEY = "follow:followers:empty:%s";    // 팔로워가 없는 사용자

	// 원자적 업데이트: 자바 스크립트를 하나의 명령어로 취급
	private static final String ADD_FOLLOW_SCRIPT = """
		    local isNew = redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
		    if isNew == 1 then
		        redis.call('ZADD', KEYS[2], ARGV[1], ARGV[3])
		        redis.call('DEL', KEYS[3])
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

	// [팔로우 생성] 팔로우 추가
	public void addFollow(UUID followerId, UUID followeeId) {
		// 팔로워 없는 사용자 Key 생성
		String emptyKey = String.format(EMPTY_FOLLOWERS_KEY, followeeId);

		// 팔로잉 및 팔로워 Key 생성
		String followingKey = String.format(FOLLOWING_KEY, followerId);
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		long timestamp = System.currentTimeMillis();

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			ADD_FOLLOW_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		Long result = stringRedisTemplate.execute(
			script,
			List.of(followingKey, followersKey, emptyKey),
			String.valueOf(timestamp),
			followeeId.toString(),
			followerId.toString()
		);

		// 메모리 관리: 최대 7일 보관
		stringRedisTemplate.expire(followingKey, Duration.ofDays(7));
		stringRedisTemplate.expire(followersKey, Duration.ofDays(7));
	}

	// [사용자의 특정 사용자 팔로우 여부 조회] 팔로우 여부 반환
	public Boolean isFollowing(UUID followerId, UUID followeeId) {
		// 팔로잉 Key 생성
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		// 유효성 검증: 캐시 존재 유무
		if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(followingKey))) {
			return null;
		}

		// 값이 존재하면 시간 반환, 존재하지 않으면 null 반환
		return stringRedisTemplate.opsForZSet().score(followingKey, followeeId.toString()) != null;
	}

	// [특정 유저의 팔로우 수 조회] 특정 사용자의 팔로워 수 조회
	public Long getFollowerCount(UUID followeeId) {
		// 캐시 상태 조회
		CacheState state = getFollowersCacheState(followeeId);

		if (state == CacheState.EMPTY) {        // 팔로워 없는 사용자
			return 0L;
		}
		if (state == CacheState.MISS) {         // 캐시 미스
			return null;
		}

		// 팔로워 Key 생성
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);

		// Set 전체 크기 반환
		Long count = stringRedisTemplate.opsForZSet().zCard(followersKey);

		return count != null
			? count
			: 0L;
	}

	// [팔로우 삭제] 팔로우 취소
	public void removeFollow(UUID followeeId, UUID followerId) {
		// 팔로잉 및 팔로워 Key 생성
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		String followingKey = String.format(FOLLOWING_KEY, followerId);

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			REMOVE_FOLLOW_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(followingKey, followersKey),
			followeeId.toString(),
			followerId.toString()
		);
	}

	// 특정 사용자의 팔로워 목록 백필
	public void initFollowers(UUID followeeId, Collection<UUID> followerIds) {

		if (followerIds == null || followerIds.isEmpty()) {
			// 팔로워가 없는 사용자 Key 생성
			String emptyKey = String.format(EMPTY_FOLLOWERS_KEY, followeeId);

			// DB 반복 조회 방지: 10분동안 팔로워 없는 사용자 상태 유지
			stringRedisTemplate.opsForValue().set(emptyKey, "1", Duration.ofMinutes(10));

			return;
		}

		// 특정 팔로워 Key 생성
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		long timestamp = System.currentTimeMillis();

		// Tuple set 타입으로 변환
		Set<ZSetOperations.TypedTuple<String>> tuples = followerIds.stream()
			.map(id -> ZSetOperations.TypedTuple.of(
				id.toString(),
				(double)timestamp)
			)
			.collect(Collectors.toSet());

		// Redis 저장
		stringRedisTemplate.opsForZSet().add(followersKey, tuples);

		// 최대 7일 보관
		stringRedisTemplate.expire(followersKey, Duration.ofDays(7));
	}

	// 공통 메서드: 특정 사용자의 캐시 상태를 확인하여 Enum으로 반환
	private CacheState getFollowersCacheState(UUID followeeId) {
		// 팔로워가 없는 사용자인 경우
		String emptyKey = String.format(EMPTY_FOLLOWERS_KEY, followeeId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(emptyKey))) {
			return CacheState.EMPTY;
		}

		// 사용자가 존재할 경우
		String followersKey = String.format(FOLLOWERS_KEY, followeeId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(followersKey))) {
			return CacheState.EXISTS;
		}

		return CacheState.MISS;
	}
}
