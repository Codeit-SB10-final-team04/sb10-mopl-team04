package com.team04.mopl.watching.store;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// contentId별 시청 중인 유저와 세션 정보를 관리하는 Redis 저장소
// 멀티탭 참조 카운팅: sessionId 기반 Set으로 첫 세션/마지막 세션 판별
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionStore {

	private final StringRedisTemplate redisTemplate;

	private static final String SESSIONS_KEY = "watching:sessions:%s:%s";
	private static final String INFO_KEY = "watching:info:%s:%s";
	private static final String USER_CONTENTS_KEY = "watching:user-contents:%s";

	// 시청자 추가 (멀티탭: 첫 세션이면 세션 정보 반환, 추가 탭이면 empty)
	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId) {
		return addWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> addWatcher(UUID contentId, UUID userId, String sessionId) {
		String sessionsKey = String.format(SESSIONS_KEY, contentId, userId);
		redisTemplate.opsForSet().add(sessionsKey, sessionId);

		Long size = redisTemplate.opsForSet().size(sessionsKey);
		if (size != null && size == 1) {
			WatchingSessionInfo info = WatchingSessionInfo.create();
			String infoKey = String.format(INFO_KEY, contentId, userId);
			redisTemplate.opsForHash().put(infoKey, "id", info.id().toString());
			redisTemplate.opsForHash().put(infoKey, "joinedAt", info.joinedAt().toString());
			redisTemplate.opsForSet().add(String.format(USER_CONTENTS_KEY, userId), contentId.toString());
			return Optional.of(info);
		}
		return Optional.empty();
	}

	// 시청자 제거 (멀티탭: 마지막 세션이면 세션 정보 반환, 아직 남았으면 empty)
	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId) {
		return removeWatcher(contentId, userId, "default");
	}

	public Optional<WatchingSessionInfo> removeWatcher(UUID contentId, UUID userId, String sessionId) {
		String sessionsKey = String.format(SESSIONS_KEY, contentId, userId);
		redisTemplate.opsForSet().remove(sessionsKey, sessionId);

		Long size = redisTemplate.opsForSet().size(sessionsKey);
		if (size != null && size == 0) {
			WatchingSessionInfo info = getInfo(contentId, userId);
			redisTemplate.delete(sessionsKey);
			redisTemplate.delete(String.format(INFO_KEY, contentId, userId));
			redisTemplate.opsForSet().remove(String.format(USER_CONTENTS_KEY, userId), contentId.toString());
			return Optional.ofNullable(info);
		}
		return Optional.empty();
	}

	public long getWatcherCount(UUID contentId) {
		Set<String> keys = redisTemplate.keys("watching:info:" + contentId + ":*");
		return keys.size();
	}

	public Map<UUID, WatchingSessionInfo> getWatchers(UUID contentId) {
		Set<String> keys = redisTemplate.keys("watching:info:" + contentId + ":*");
		if (keys.isEmpty()) {
			return Map.of();
		}
		Map<UUID, WatchingSessionInfo> result = new HashMap<>();
		for (String key : keys) {
			UUID userId = UUID.fromString(key.substring(key.lastIndexOf(":") + 1));
			WatchingSessionInfo info = getInfo(contentId, userId);
			if (info != null) {
				result.put(userId, info);
			}
		}
		return result;
	}

	public boolean isWatching(UUID contentId, UUID userId) {
		return redisTemplate.hasKey(String.format(INFO_KEY, contentId, userId));
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
			WatchingSessionInfo info = getInfo(contentId, userId);
			if (info != null) {
				result.put(contentId, info);
			}
		}
		return result;
	}

	private WatchingSessionInfo getInfo(UUID contentId, UUID userId) {
		String infoKey = String.format(INFO_KEY, contentId, userId);
		Object id = redisTemplate.opsForHash().get(infoKey, "id");
		Object joinedAt = redisTemplate.opsForHash().get(infoKey, "joinedAt");
		if (id == null || joinedAt == null) {
			return null;
		}
		return new WatchingSessionInfo(UUID.fromString(id.toString()), Instant.parse(joinedAt.toString()));
	}
}
