package com.team04.mopl.directmessage.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.enums.CacheState;
import com.team04.mopl.directmessage.entity.DirectMessage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DirectMessageRedisStore {

	private static final String DM_ROOM_KEY = "dm:room:%s:messages";                    // 특정 대화방의 메시지둘
	private static final String UNREAD_COUNT_KEY = "dm:unread:%s:%s";                   // 특정 사용자가 읽지 않은 특정 대화방의 DM 개수
	private static final String GLOBAL_UNREAD_COUNT_KEY = "dm:unread:global:%s";        // 특정 사용자가 읽지 않은 모든 DM 개수
	private static final String EMPTY_DM_ROOM_KEY = "dm:room:empty:%s";                 // DM이 존재하지 않는 대화방

	// 원자적 업데이트: 자바 스크립트를 하나의 명령어로 취급
	private static final String ADD_DM_SCRIPT = """
		    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
		    redis.call('DEL', KEYS[2])
		    return 1
		""";

	// 원자적 감소
	private static final String DECREMENT_UNREAD_SCRIPT = """
		    local count = tonumber(redis.call('GET', KEYS[1]))
		    if count and count > 0 then
		        redis.call('DECR', KEYS[1])
		        redis.call('DECR', KEYS[2])
		        return 1
		    end
		    return 0
		""";

	private final StringRedisTemplate stringRedisTemplate;

	// [DM 생성] DM 추가
	public void addDirectMessage(UUID conversationId, DirectMessage message) {
		// DM Key 생성: 빈 대화방 및 DM 저장 대화방
		String key = String.format(DM_ROOM_KEY, conversationId);
		String emptyKey = String.format(EMPTY_DM_ROOM_KEY, conversationId);

		long timestamp = message.getCreatedAt().toEpochMilli();

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			ADD_DM_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, emptyKey),
			String.valueOf(timestamp),
			message.getId().toString()
		);

		// 메모리 관리: 최대 7일 보관
		stringRedisTemplate.expire(key, Duration.ofDays(7));
	}

	// [DM 생성] 특정 사용자의 특정 대화방 안 읽음 DM 개수 증가
	public void incrementUnreadCount(UUID receiverId, UUID conversationId) {
		// 특정 사용자의 안 읽음 DM 개수 Key 생성: 특정 대화방 및 전체 대화방
		String key = String.format(UNREAD_COUNT_KEY, receiverId, conversationId);
		String globalKey = String.format(GLOBAL_UNREAD_COUNT_KEY, receiverId);

		// 안 읽음 개수 증가
		stringRedisTemplate.opsForValue().increment(key);
		stringRedisTemplate.opsForValue().increment(globalKey);
	}

	// [DM 읽음 상태 생성] 특정 사용자의 특정 대화방 및 전체 대화방 안 읽은 개수 감소
	public void decrementUnreadCount(UUID userId, UUID conversationId) {
		// 특정 사용자의 안 읽음 DM 개수 Key 생성: 특정 대화방 및 전체 대화방
		String key = String.format(UNREAD_COUNT_KEY, userId, conversationId);
		String globalKey = String.format(GLOBAL_UNREAD_COUNT_KEY, userId);

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			DECREMENT_UNREAD_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, globalKey)
		);
	}

	// [DM 목록 조회] 특정 대화방의 DM ID 목록 조회
	public Set<String> getDirectMessageIdsByCursor(UUID conversationId, long cursorTimestamp, int limit) {
		// 캐시 상태 조회
		CacheState state = getCacheState(conversationId);

		if (state == CacheState.EMPTY) {        // 빈 대화방
			return Collections.emptySet();
		}
		if (state == CacheState.MISS) {         // 캐시 미스
			return null;
		}

		// 특정 대화방 Key 생성
		String key = String.format(DM_ROOM_KEY, conversationId);

		// 특정 시점 (CreatedAt) 이전의 DM 내림차순 조회
		return stringRedisTemplate.opsForZSet().reverseRangeByScore(
			key,
			0,
			cursorTimestamp,
			0, limit
		);
	}

	// [DM 목록 조회] 특정 사용자의 전체 안 읽음 DM 개수 조회
	public Long getGlobalUnreadCount(UUID userId) {
		// 특정 사용자의 전체 대화방 안 읽으 DM 개수 Key 생성
		String globalKey = String.format(GLOBAL_UNREAD_COUNT_KEY, userId);

		// Set 전체 크기 반환
		String countStr = stringRedisTemplate.opsForValue().get(globalKey);

		return countStr != null
			? Long.parseLong(countStr)
			: 0L;
	}

	// [DM 목록 조회] 특정 대화방의 총 DM 개수 조회
	public Long getRoomMessageTotalCount(UUID conversationId) {
		// 캐시 상태 조회
		CacheState state = getCacheState(conversationId);

		if (state == CacheState.EMPTY) {    // 빈 대화방
			return 0L;
		}
		if (state == CacheState.MISS) {     // 캐시 미스
			return null;
		}

		// 특정 대화방의 Key 생성
		String key = String.format(DM_ROOM_KEY, conversationId);

		// Set 전체 크기 반환
		return stringRedisTemplate.opsForZSet().zCard(key);
	}

	// 유효성 검증: 캐시 미스 (Cache Miss)
	public boolean hasRoomMessages(UUID conversationId) {
		return getCacheState(conversationId) != CacheState.MISS;
	}

	// 특정 대화방의 DM 목록 백필
	public void initDirectMessages(UUID conversationId, java.util.List<DirectMessage> messages) {

		// 특정 대화방의 DM이 없는 경우
		if (messages == null || messages.isEmpty()) {
			// 비어있는 대화방 Key 생성
			String emptyKey = String.format(EMPTY_DM_ROOM_KEY, conversationId);

			// DB 반복 조회 방지: 10분동안 빈 대화방 상태 유지
			stringRedisTemplate.opsForValue().set(emptyKey, "1", Duration.ofMinutes(10));

			return;
		}

		// 특정 대화방의 Key 생성
		String key = String.format(DM_ROOM_KEY, conversationId);

		// List 목록을 Tuple Set 타입으로 변환
		Set<ZSetOperations.TypedTuple<String>> tuples = messages.stream()
			.map(message -> ZSetOperations.TypedTuple.of(
				message.getId().toString(),
				(double)message.getCreatedAt().toEpochMilli()
			))
			.collect(Collectors.toSet());

		// Redis 저장
		stringRedisTemplate.opsForZSet().add(key, tuples);

		// 최대 7일 보관
		stringRedisTemplate.expire(key, Duration.ofDays(7));
	}

	// 공통 메서드: 특정 대화방의 캐시 상태를 확인하여 Enum으로 반환
	private CacheState getCacheState(UUID conversationId) {
		// 특정 대화방이 빈 방일 경우
		String emptyKey = String.format(EMPTY_DM_ROOM_KEY, conversationId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(emptyKey))) {
			return CacheState.EMPTY;
		}

		// 특정 대화방이 존재할 경우
		String key = String.format(DM_ROOM_KEY, conversationId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
			return CacheState.EXISTS;
		}

		return CacheState.MISS;
	}
}