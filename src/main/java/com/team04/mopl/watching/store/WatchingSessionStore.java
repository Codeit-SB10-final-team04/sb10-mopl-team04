package com.team04.mopl.watching.store;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시청 세션 통합 Redis 저장소
// 키 구조:
//   watching:session:{sessionId}      → Hash {userId, contentId, joinedAt} (세션 매핑 + 메타)
//   watching:viewers:{contentId}      → Sorted Set (member=userId, score=joinedAt millis) (시청자 목록)
//   watching:user-sessions:{userId}   → Set<sessionId> (userId→sessionId 역참조)
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionStore {

	private final StringRedisTemplate redisTemplate;

	private static final String SESSION_KEY = "watching:session:%s";
	private static final String VIEWERS_KEY = "watching:viewers:%s";
	private static final String USER_SESSIONS_KEY = "watching:user-sessions:%s";
	private static final long TTL_SECONDS = 86400; // 24시간

	// Lua: 세션 등록 + viewers 추가 (원자적)
	// 반환: "1" = 첫 탭(JOIN), "0" = 추가 탭 또는 이미 등록됨
	private static final DefaultRedisScript<String> JOIN_SCRIPT = new DefaultRedisScript<>("""
		local existingContentId = redis.call('HGET', KEYS[1], 'contentId')
		if existingContentId then
			return '0'
		end
		redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'contentId', ARGV[2], 'joinedAt', ARGV[3])
		redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
		redis.call('SADD', KEYS[3], ARGV[4])
		redis.call('EXPIRE', KEYS[3], tonumber(ARGV[5]))
		if redis.call('ZSCORE', KEYS[2], ARGV[1]) then
			return '0'
		end
		redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), ARGV[1])
		redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5]))
		return '1'
		""", String.class);

	// Lua: 세션 삭제 + 마지막 탭이면 viewers에서 제거 (원자적)
	// 반환: joinedAt = 마지막 탭(LEAVE), "0" = 다른 탭 남음, "-1" = 세션 없음
	private static final DefaultRedisScript<String> LEAVE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('EXISTS', KEYS[1]) == 0 then
			return '-1'
		end
		local joinedAt = redis.call('HGET', KEYS[1], 'joinedAt')
		redis.call('DEL', KEYS[1])
		redis.call('SREM', KEYS[3], ARGV[3])
		local remaining = redis.call('SMEMBERS', KEYS[3])
		for _, sid in ipairs(remaining) do
			local cid = redis.call('HGET', 'watching:session:' .. sid, 'contentId')
			if cid == ARGV[2] then
				return '0'
			end
		end
		redis.call('ZREM', KEYS[2], ARGV[1])
		return joinedAt
		""", String.class);

	// Lua: CONNECT 시 session Hash + user-sessions 등록 (원자적 + expire)
	private static final DefaultRedisScript<String> REGISTER_SCRIPT = new DefaultRedisScript<>("""
		redis.call('HSET', KEYS[1], 'userId', ARGV[1])
		redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
		redis.call('SADD', KEYS[2], ARGV[2])
		redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
		return '1'
		""", String.class);

	// CONNECT 시 최소 정보 저장 (userId만, contentId는 SUBSCRIBE 시 join에서 추가)
	public void registerSession(String sessionId, UUID userId) {
		List<String> keys = Arrays.asList(
			String.format(SESSION_KEY, sessionId),
			String.format(USER_SESSIONS_KEY, userId)
		);

		redisTemplate.execute(REGISTER_SCRIPT, keys,
			userId.toString(), sessionId, String.valueOf(TTL_SECONDS));
	}

	// 시청 세션 입장 (Lua 원자적 처리)
	// 반환: 첫 탭이면 joinedAt, 추가 탭이면 empty
	public Optional<Instant> join(String sessionId, UUID userId, UUID contentId) {
		Instant joinedAt = Instant.now();
		String joinedAtMillis = String.valueOf(joinedAt.toEpochMilli());

		List<String> keys = Arrays.asList(
			String.format(SESSION_KEY, sessionId),
			String.format(VIEWERS_KEY, contentId),
			String.format(USER_SESSIONS_KEY, userId)
		);

		String result = redisTemplate.execute(JOIN_SCRIPT, keys,
			userId.toString(), contentId.toString(), joinedAtMillis,
			sessionId, String.valueOf(TTL_SECONDS));

		return "1".equals(result) ? Optional.of(joinedAt) : Optional.empty();
	}

	// 시청 세션 퇴장 (Lua 원자적 처리)
	// 반환: 마지막 탭이면 joinedAt, 다른 탭 남으면 empty
	public Optional<Instant> leave(String sessionId, UUID userId, UUID contentId) {
		List<String> keys = Arrays.asList(
			String.format(SESSION_KEY, sessionId),
			String.format(VIEWERS_KEY, contentId),
			String.format(USER_SESSIONS_KEY, userId)
		);

		String result = redisTemplate.execute(LEAVE_SCRIPT, keys,
			userId.toString(), contentId.toString(), sessionId);

		if (result != null && !result.equals("0") && !result.equals("-1")) {
			return Optional.of(Instant.ofEpochMilli(Long.parseLong(result)));
		}
		return Optional.empty();
	}

	// session Hash에서 userId 조회
	public Optional<UUID> getUserId(String sessionId) {
		Object userId = redisTemplate.opsForHash().get(String.format(SESSION_KEY, sessionId), "userId");
		return userId != null ? Optional.of(UUID.fromString(userId.toString())) : Optional.empty();
	}

	// session Hash에서 contentId 조회
	public Optional<UUID> getContentId(String sessionId) {
		Object contentId = redisTemplate.opsForHash().get(String.format(SESSION_KEY, sessionId), "contentId");
		return contentId != null ? Optional.of(UUID.fromString(contentId.toString())) : Optional.empty();
	}

	// 세션 Hash 삭제 (방어적 정리용)
	public void removeSession(String sessionId) {
		redisTemplate.delete(String.format(SESSION_KEY, sessionId));
	}

	// 시청자 수 조회 (ZCARD)
	public long getViewerCount(UUID contentId) {
		Long count = redisTemplate.opsForZSet().zCard(String.format(VIEWERS_KEY, contentId));
		return count != null ? count : 0;
	}

	// 시청자 목록 조회 (Sorted Set → Map<userId, joinedAt>)
	public Map<UUID, Instant> getViewers(UUID contentId) {
		Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
			.rangeWithScores(String.format(VIEWERS_KEY, contentId), 0, -1);

		if (tuples == null || tuples.isEmpty()) {
			return Map.of();
		}

		Map<UUID, Instant> result = new HashMap<>();
		for (ZSetOperations.TypedTuple<String> tuple : tuples) {
			if (tuple.getValue() != null && tuple.getScore() != null) {
				result.put(
					UUID.fromString(tuple.getValue()),
					Instant.ofEpochMilli(tuple.getScore().longValue())
				);
			}
		}
		return result;
	}

	// 시청 중인지 확인 (ZSCORE)
	public boolean isViewing(UUID contentId, UUID userId) {
		Double score = redisTemplate.opsForZSet()
			.score(String.format(VIEWERS_KEY, contentId), userId.toString());
		return score != null;
	}

	// userId로 시청 중인 콘텐츠 조회 (user-sessions → 각 session Hash에서 contentId/joinedAt)
	public Map<UUID, Instant> getSessionsByUserId(UUID userId) {
		Set<String> sessionIds = redisTemplate.opsForSet()
			.members(String.format(USER_SESSIONS_KEY, userId));

		if (sessionIds == null || sessionIds.isEmpty()) {
			return Map.of();
		}

		Map<UUID, Instant> result = new HashMap<>();
		for (String sid : sessionIds) {
			String sessionKey = String.format(SESSION_KEY, sid);
			Object contentId = redisTemplate.opsForHash().get(sessionKey, "contentId");
			Object joinedAt = redisTemplate.opsForHash().get(sessionKey, "joinedAt");
			if (contentId != null && joinedAt != null) {
				result.put(
					UUID.fromString(contentId.toString()),
					Instant.ofEpochMilli(Long.parseLong(joinedAt.toString()))
				);
			}
		}
		return result;
	}
}
