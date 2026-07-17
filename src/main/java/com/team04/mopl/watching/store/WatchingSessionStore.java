package com.team04.mopl.watching.store;

import java.time.Instant;
import java.util.Arrays;
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

	// Lua: SADD + 첫 세션이면 info/count/역인덱스까지 원자적으로 처리
	// KEYS: [1]sessions [2]info [3]count [4]userContents [5]watchers
	// ARGV: [1]sessionId [2]infoId [3]joinedAt [4]contentId [5]userId
	// 반환: "1" = 첫 세션(JOIN), "0" = 추가 탭
	private static final DefaultRedisScript<String> ADD_WATCHER_SCRIPT = new DefaultRedisScript<>("""
		redis.call('SADD', KEYS[1], ARGV[1])
		local size = redis.call('SCARD', KEYS[1])
		if size == 1 then
			redis.call('HSET', KEYS[2], 'id', ARGV[2], 'joinedAt', ARGV[3])
			redis.call('INCR', KEYS[3])
			redis.call('SADD', KEYS[4], ARGV[4])
			redis.call('SADD', KEYS[5], ARGV[5])
			return '1'
		end
		return '0'
		""", String.class);

	// Lua: SREM + 마지막 세션이면 info 조회 후 전체 정리까지 원자적으로 처리
	// KEYS: [1]sessions [2]info [3]count [4]userContents [5]watchers
	// ARGV: [1]sessionId [2]contentId [3]userId
	// 반환: "id|joinedAt" = 마지막 세션(LEAVE), "" = 아직 탭 남음
	private static final DefaultRedisScript<String> REMOVE_WATCHER_SCRIPT = new DefaultRedisScript<>("""
		redis.call('SREM', KEYS[1], ARGV[1])
		local size = redis.call('SCARD', KEYS[1])
		if size == 0 then
			local id = redis.call('HGET', KEYS[2], 'id')
			local joinedAt = redis.call('HGET', KEYS[2], 'joinedAt')
			redis.call('DEL', KEYS[1])
			redis.call('DEL', KEYS[2])
			redis.call('DECR', KEYS[3])
			redis.call('SREM', KEYS[4], ARGV[2])
			redis.call('SREM', KEYS[5], ARGV[3])
			if id and joinedAt then
				return id .. '|' .. joinedAt
			end
			return ''
		end
		return ''
		""", String.class);

	// Lua: sessions Set 전체 삭제 + 강제 정리 (DISCONNECT 시 좀비 세션 방지)
	// KEYS: [1]sessions [2]info [3]count [4]userContents [5]watchers
	// ARGV: [1]contentId [2]userId
	// 반환: "id|joinedAt" = 세션 존재했으면, "" = 이미 없었음
	private static final DefaultRedisScript<String> FORCE_REMOVE_WATCHER_SCRIPT = new DefaultRedisScript<>("""
		local size = redis.call('SCARD', KEYS[1])
		if size == 0 then
			return ''
		end
		local id = redis.call('HGET', KEYS[2], 'id')
		local joinedAt = redis.call('HGET', KEYS[2], 'joinedAt')
		redis.call('DEL', KEYS[1])
		redis.call('DEL', KEYS[2])
		redis.call('DECR', KEYS[3])
		redis.call('SREM', KEYS[4], ARGV[1])
		redis.call('SREM', KEYS[5], ARGV[2])
		if id and joinedAt then
			return id .. '|' .. joinedAt
		end
		return ''
		""", String.class);

	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId) {
		return addWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId, String sessionId) {
		WatchingSessionInfo info = WatchingSessionInfo.create();

		List<String> keys = Arrays.asList(
			String.format(SESSIONS_KEY, contentId, userId),
			String.format(INFO_KEY, contentId, userId),
			String.format(COUNT_KEY, contentId),
			String.format(USER_CONTENTS_KEY, userId),
			String.format(WATCHERS_KEY, contentId)
		);

		String result = redisTemplate.execute(ADD_WATCHER_SCRIPT, keys,
			sessionId, info.id().toString(), info.joinedAt().toString(),
			contentId.toString(), userId.toString());

		return "1".equals(result) ? Optional.of(info) : Optional.empty();
	}

	// 강제 퇴장: sessions Set 전체 삭제 (DISCONNECT 시 좀비 세션 방지)
	public Optional<WatchingSessionInfo> forceRemoveWatcher(UUID contentId, UUID userId) {
		List<String> keys = Arrays.asList(
			String.format(SESSIONS_KEY, contentId, userId),
			String.format(INFO_KEY, contentId, userId),
			String.format(COUNT_KEY, contentId),
			String.format(USER_CONTENTS_KEY, userId),
			String.format(WATCHERS_KEY, contentId)
		);

		String result = redisTemplate.execute(FORCE_REMOVE_WATCHER_SCRIPT, keys,
			contentId.toString(), userId.toString());

		if (result != null && !result.isEmpty()) {
			String[] parts = result.split("\\|", 2);
			return Optional.of(new WatchingSessionInfo(
				UUID.fromString(parts[0]),
				Instant.parse(parts[1])
			));
		}
		return Optional.empty();
	}

	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId) {
		return removeWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId, String sessionId) {
		List<String> keys = Arrays.asList(
			String.format(SESSIONS_KEY, contentId, userId),
			String.format(INFO_KEY, contentId, userId),
			String.format(COUNT_KEY, contentId),
			String.format(USER_CONTENTS_KEY, userId),
			String.format(WATCHERS_KEY, contentId)
		);

		String result = redisTemplate.execute(REMOVE_WATCHER_SCRIPT, keys,
			sessionId, contentId.toString(), userId.toString());

		if (result != null && !result.isEmpty()) {
			String[] parts = result.split("\\|", 2);
			return Optional.of(new WatchingSessionInfo(
				UUID.fromString(parts[0]),
				Instant.parse(parts[1])
			));
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
