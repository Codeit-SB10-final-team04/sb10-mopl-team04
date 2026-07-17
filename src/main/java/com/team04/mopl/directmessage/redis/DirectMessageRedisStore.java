package com.team04.mopl.directmessage.redis;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.enums.CacheState;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DirectMessageRedisStore {

	private static final String DM_ROOM_KEY = "dm:room:%s:messages";                    // 특정 대화방의 메시지둘
	private static final String UNREAD_COUNT_KEY = "dm:unread:%s:%s";                   // 특정 사용자가 읽지 않은 특정 대화방의 DM 개수
	private static final String GLOBAL_UNREAD_COUNT_KEY = "dm:unread:global:%s";        // 특정 사용자가 읽지 않은 모든 DM 개수
	private static final String EMPTY_DM_ROOM_KEY = "dm:room:empty:%s";                 // DM이 존재하지 않는 대화방

	private static final int MAX_CACHE_MESSAGES = 500;

	// 원자적 추가
	private static final String ADD_DM_AND_INCR_SCRIPT = """
		    local added = redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
		    redis.call('DEL', KEYS[2])
		    if added > 0 then
		        redis.call('INCR', KEYS[3])
		        redis.call('INCR', KEYS[4])
		    end
		    return added
		""";

	// 원자적 감소
	private static final String RESET_UNREAD_SCRIPT = """
		    local roomCount = tonumber(redis.call('GET', KEYS[1]) or '0')
		    if roomCount > 0 then
		        -- 1. 글로벌 카운트에서 이 방의 안 읽음 개수만큼 한 번에 빼기
		        redis.call('DECRBY', KEYS[2], roomCount)
		        -- 2. 이 방의 안 읽음 카운트는 0이 되었으므로 키 삭제
		        redis.call('DEL', KEYS[1])
		    end
		    return roomCount
		""";

	private final StringRedisTemplate stringRedisTemplate;

	// [DM 생성] DM 추가
	public void addDirectMessage(UUID conversationId, UUID receiverId, DirectMessageDto directMessageDto) {
		// DM Key 생성: 빈 대화방 및 DM 저장 대화방
		String key = String.format(DM_ROOM_KEY, conversationId);
		String emptyKey = String.format(EMPTY_DM_ROOM_KEY, conversationId);

		// 특정 사용자의 안 읽음 DM 개수 Key 생성: 특정 대화방 및 전체 대화방
		String unreadKey = String.format(UNREAD_COUNT_KEY, receiverId, conversationId);
		String globalKey = String.format(GLOBAL_UNREAD_COUNT_KEY, receiverId);

		long timestamp = directMessageDto.createdAt().toEpochMilli();

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			ADD_DM_AND_INCR_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, emptyKey, unreadKey, globalKey),
			String.valueOf(timestamp),
			directMessageDto.id().toString()
		);

		// 메모리 관리: 메시지 최대 개수 유지
		stringRedisTemplate.opsForZSet().removeRange(key, 0, -(MAX_CACHE_MESSAGES + 1));

		// 캐시 TTL 갱신
		refreshTtl(key, unreadKey, globalKey);
	}

	// [DM 읽음 상태 생성] 특정 사용자의 특정 대화방 및 전체 대화방 안 읽은 개수 감소
	public void decrementUnreadCount(UUID userId, UUID conversationId) {
		// 특정 사용자의 안 읽음 DM 개수 Key 생성: 특정 대화방 및 전체 대화방
		String key = String.format(UNREAD_COUNT_KEY, userId, conversationId);
		String globalKey = String.format(GLOBAL_UNREAD_COUNT_KEY, userId);

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			RESET_UNREAD_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, globalKey)
		);

		// 전체 카운트 TTL 연장
		refreshTtl(globalKey);
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

		// TTL 연장
		refreshTtl(key);

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

		// TTL 연장
		refreshTtl(key);

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
			stringRedisTemplate.opsForValue().set(emptyKey, "1", 10, TimeUnit.MINUTES);

			return;
		}

		// 특정 대화방의 Key 생성
		String key = String.format(DM_ROOM_KEY, conversationId);

		// 메시지 개수 제한
		int skipCount = Math.max(0, messages.size() - MAX_CACHE_MESSAGES);

		// List 목록을 Tuple Set 타입으로 변환
		Set<ZSetOperations.TypedTuple<String>> tuples = messages.stream()
			.skip(skipCount)
			.map(message -> ZSetOperations.TypedTuple.of(
				message.getId().toString(),
				(double)message.getCreatedAt().toEpochMilli()
			))
			.collect(Collectors.toSet());

		// Redis 저장
		stringRedisTemplate.opsForZSet().add(key, tuples);

		// 최대 7일 보관
		refreshTtl(key);
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

	// 공통 메서드: TTL 갱신
	private void refreshTtl(String... keys) {
		for (String key : keys) {
			stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
		}
	}
}