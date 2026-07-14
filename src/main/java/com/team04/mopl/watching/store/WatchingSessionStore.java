package com.team04.mopl.watching.store;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// contentId별 시청 중인 유저와 세션 정보를 관리하는 Redis 저장소
// 멀티탭 참조 카운팅: Lua 스크립트로 SADD + SCARD를 원자적으로 처리
// 키 구조:
//   watching:sessions:{contentId}:{userId} → Set<sessionId> (멀티탭)
//   watching:info:{contentId}:{userId} → Hash {id, joinedAt}
//   watching:count:{contentId} → String (시청자 수 카운터)
//   watching:user-contents:{userId} → Set<contentId> (역인덱스)
//   watching:watchers:{contentId} → Set<userId> (시청자 목록)
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionStore {

	private final StringRedisTemplate redisTemplate;

	private static final String SESSIONS_KEY = "watching:sessions:%s:%s";
	private static final String INFO_KEY = "watching:info:%s:%s";
	private static final String COUNT_KEY = "watching:count:%s";
	private static final String USER_CONTENTS_KEY = "watching:user-contents:%s";
	private static final String WATCHERS_KEY = "watching:watchers:%s";

	// Lua: SADD 후 SCARD를 원자적으로 반환
	private static final DefaultRedisScript<Long> ADD_AND_COUNT = new DefaultRedisScript<>(
		"redis.call('SADD', KEYS[1], ARGV[1]); return redis.call('SCARD', KEYS[1])", Long.class);

	// Lua: SREM 후 SCARD를 원자적으로 반환
	private static final DefaultRedisScript<Long> REMOVE_AND_COUNT = new DefaultRedisScript<>(
		"redis.call('SREM', KEYS[1], ARGV[1]); return redis.call('SCARD', KEYS[1])", Long.class);

	// 시청자 추가 (Lua 스크립트로 원자적 처리, 첫 세션이면 세션 정보 반환)
	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId) {
		return addWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId, String sessionId) {
		String sessionsKey = String.format(SESSIONS_KEY, contentId, userId);

		// Lua: SADD + SCARD 원자적 실행
		Long size = redisTemplate.execute(ADD_AND_COUNT, List.of(sessionsKey), sessionId);

		if (size != null && size == 1) {
			// 첫 세션 → 세션 정보 생성 + 카운터 증가 + 역인덱스 추가
			WatchingSessionInfo info = WatchingSessionInfo.create();
			String infoKey = String.format(INFO_KEY, contentId, userId);
			redisTemplate.opsForHash().put(infoKey, "id", info.id().toString());
			redisTemplate.opsForHash().put(infoKey, "joinedAt", info.joinedAt().toString());
			redisTemplate.opsForValue().increment(String.format(COUNT_KEY, contentId));
			redisTemplate.opsForSet().add(String.format(USER_CONTENTS_KEY, userId), contentId.toString());
			redisTemplate.opsForSet().add(String.format(WATCHERS_KEY, contentId), userId.toString());
			return Optional.of(info);
		}
		return Optional.empty();
	}

	// 시청자 제거 (Lua 스크립트로 원자적 처리, 마지막 세션이면 세션 정보 반환)
	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId) {
		return removeWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId, String sessionId) {
		String sessionsKey = String.format(SESSIONS_KEY, contentId, userId);

		// Lua: SREM + SCARD 원자적 실행
		Long size = redisTemplate.execute(REMOVE_AND_COUNT, List.of(sessionsKey), sessionId);

		if (size != null && size == 0) {
			// 마지막 세션 → 정리 + 카운터 감소
			Optional<WatchingSessionInfo> info = getInfo(contentId, userId);
			redisTemplate.delete(sessionsKey);
			redisTemplate.delete(String.format(INFO_KEY, contentId, userId));
			redisTemplate.opsForValue().decrement(String.format(COUNT_KEY, contentId));
			redisTemplate.opsForSet().remove(String.format(USER_CONTENTS_KEY, userId), contentId.toString());
			redisTemplate.opsForSet().remove(String.format(WATCHERS_KEY, contentId), userId.toString());
			return info;
		}
		return Optional.empty();
	}

	// 시청자 수 조회 (카운터 기반, KEYS 명령 사용하지 않음)
	public long getWatcherCount(UUID contentId) {
		String value = redisTemplate.opsForValue().get(String.format(COUNT_KEY, contentId));
		return value != null ? Long.parseLong(value) : 0;
	}

	// 시청자 목록 조회 (watchers Set 기반, KEYS 명령 사용하지 않음)
	public Map<UUID, WatchingSessionInfo> getWatchers(UUID contentId) {
		Set<String> userIds = redisTemplate.opsForSet().members(String.format(WATCHERS_KEY, contentId));
		if (userIds == null || userIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, WatchingSessionInfo> result = new HashMap<>();
		for (String userIdStr : userIds) {
			UUID userId = UUID.fromString(userIdStr);
			getInfo(contentId, userId).ifPresent(info -> result.put(userId, info));
		}
		return result;
	}

	public boolean isWatching(UUID contentId, UUID userId) {
		return Boolean.TRUE.equals(
			redisTemplate.opsForSet().isMember(String.format(WATCHERS_KEY, contentId), userId.toString()));
	}

	public Set<UUID> getWatchingContentIds(UUID userId) {
		Set<String> members = redisTemplate.opsForSet().members(String.format(USER_CONTENTS_KEY, userId));
		if (members == null) {
			return Set.of();
		}
		return members.stream().map(UUID::fromString).collect(Collectors.toSet());
	}

	public Map<UUID, WatchingSessionInfo> getWatchingSessions(UUID userId) {
		Set<UUID> contentIds = getWatchingContentIds(userId);
		Map<UUID, WatchingSessionInfo> result = new HashMap<>();
		for (UUID contentId : contentIds) {
			getInfo(contentId, userId).ifPresent(info -> result.put(contentId, info));
		}
		return result;
	}

	private Optional<WatchingSessionInfo> getInfo(UUID contentId, UUID userId) {
		String infoKey = String.format(INFO_KEY, contentId, userId);
		Object id = redisTemplate.opsForHash().get(infoKey, "id");
		Object joinedAt = redisTemplate.opsForHash().get(infoKey, "joinedAt");
		if (id == null || joinedAt == null) {
			return Optional.empty();
		}
		return Optional.of(new WatchingSessionInfo(UUID.fromString(id.toString()), Instant.parse(joinedAt.toString())));
	}
}
