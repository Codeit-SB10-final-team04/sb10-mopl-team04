package com.team04.mopl.conversation.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.enums.CacheState;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationRedisStore {

	public static final UUID EMPTY_MARKER = new UUID(0, 0);

	private static final String CONVERSATION_MAPPING_KEY = "conv:mapping:%s:%s";                            // 대화방 Key
	private static final String CONVERSATION_PARTICIPANTS_KEY = "conv:room:%s:participants";                // 대화 참여자 목록 Key

	private static final String EMPTY_CONVERSATION_MAPPING_KEY = "conv:mapping:empty:%s:%s";                // 비어있는 대화방 Key
	private static final String EMPTY_CONVERSATION_PARTICIPANTS_KEY = "conv:room:empty:%s:participants";    // 빈 대화 참여자 목록 Key

	// 원자적 업데이트: 자바 스크립트를 하나의 명령어로 취급
	private static final String SAVE_MAPPING_SCRIPT = """
		    redis.call('SET', KEYS[1], ARGV[1], 'EX', 2592000) -- 30일
		    redis.call('DEL', KEYS[2])
		    return 1
		""";

	// 원자적 업데이트
	private static final String SAVE_PARTICIPANTS_SCRIPT = """
		    redis.call('SADD', KEYS[1], unpack(ARGV))
		    redis.call('EXPIRE', KEYS[1], 2592000) -- 30일
		    redis.call('DEL', KEYS[2])
		    return 1
		""";

	private final StringRedisTemplate stringRedisTemplate;

	// [대화 생성] 대화 추가
	public void addConversation(UUID user1, UUID user2, UUID conversationId) {
		// 대화 Key 생성: 빈 대화방 및 대화방
		String key = generateMappingKey(user1, user2);
		String emptyKey = generateEmptyMappingKey(user1, user2);

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			SAVE_MAPPING_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, emptyKey),
			conversationId.toString()
		);

		// 최대 30일 보관
		stringRedisTemplate.expire(key, Duration.ofDays(30));
	}

	// [대화 생성] 특정 대화방의 참여자 목록 추가
	public void addParticipants(UUID conversationId, Set<UUID> participantIds) {
		// 대화 참여자 목록 Key 생성: 빈 참여자 목록 및 참여자 목록
		String key = String.format(CONVERSATION_PARTICIPANTS_KEY, conversationId);
		String emptyKey = String.format(EMPTY_CONVERSATION_PARTICIPANTS_KEY, conversationId);

		// UUID 목록을 String 목록으로 변환
		String[] participantIdStrings = participantIds.stream()
			.map(UUID::toString)
			.toArray(String[]::new);

		// 스크립트 생성
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(
			SAVE_PARTICIPANTS_SCRIPT,
			Long.class
		);

		// 스크립트 실행
		stringRedisTemplate.execute(
			script,
			List.of(key, emptyKey),
			(Object[])participantIdStrings
		);

		// 최대 30일 보관
		stringRedisTemplate.expire(key, Duration.ofDays(30));
	}

	// [대화 조회] 특정 대화방의 ID 단건 조회
	public UUID getConversationId(UUID user1, UUID user2) {
		// 캐시 조회
		CacheState state = getMappingCacheState(user1, user2);

		if (state == CacheState.EMPTY) {        // 빈 대화방
			return EMPTY_MARKER;
		}
		if (state == CacheState.MISS) {         // 캐시 미스
			return null;
		}

		// 대화 Key 생성
		String key = generateMappingKey(user1, user2);

		// 대화 ID 조회
		String conversationIdStr = stringRedisTemplate.opsForValue().get(key);

		return conversationIdStr != null
			? UUID.fromString(conversationIdStr)
			: null;
	}

	// [대화 조회] 대화방의 전체 참여자 목록 조회
	public Set<UUID> getParticipants(UUID conversationId) {
		// 캐시 조회
		CacheState state = getParticipantsCacheState(conversationId);

		if (state == CacheState.EMPTY) {
			return Collections.emptySet();        // 빈 참여자 목록
		}
		if (state == CacheState.MISS) {           // 캐시 미스
			return null;
		}

		// 대화 참여자 목록 Key 생성: 빈 참여자 목록 및 참여자 목록
		String key = String.format(CONVERSATION_PARTICIPANTS_KEY, conversationId);
		Set<String> members = stringRedisTemplate.opsForSet().members(key);

		if (members == null || members.isEmpty()) {
			return null;
		}

		return members.stream()
			.map(UUID::fromString)
			.collect(Collectors.toSet());
	}

	// 특정 대화방 백필
	public void initConversationMapping(UUID user1, UUID user2, UUID conversationId) {

		// 특정 대화방이 없는 경우
		if (conversationId == null) {
			// 비어있는 대화방 Key 생성
			String emptyKey = generateEmptyMappingKey(user1, user2);

			// DB 반복 조회 방지: 10분동안 빈 대화방 상태 유지
			stringRedisTemplate.opsForValue().set(emptyKey, "1", Duration.ofMinutes(10));

			return;
		}

		// Redis 저장
		addConversation(user1, user2, conversationId);
	}

	// 특정 대화방의 참여자 백필
	public void initParticipants(UUID conversationId, Set<UUID> participantIds) {

		// 대화 참여자가 없는 경우
		if (participantIds == null || participantIds.isEmpty()) {
			// 비어있는 참여자 목록 Key 생성
			String emptyKey = String.format(EMPTY_CONVERSATION_PARTICIPANTS_KEY, conversationId);

			// DB 반복 조회 방지: 10분동안 빈 대화방 상태 유지
			stringRedisTemplate.opsForValue().set(emptyKey, "1", Duration.ofMinutes(10));

			return;
		}

		// Redis 저장
		addParticipants(conversationId, participantIds);
	}

	// 대화 캐시 상태 조회: 특정 대화방의 캐시 상태를 확인하여 Enum으로 반환
	public CacheState getMappingCacheState(UUID user1, UUID user2) {
		// 특정 대화방이 빈 방일 경우
		String emptyKey = generateEmptyMappingKey(user1, user2);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(emptyKey))) {
			return CacheState.EMPTY;
		}

		// 특정 대화방이 존재할 경우
		String key = generateMappingKey(user1, user2);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
			return CacheState.EXISTS;
		}

		return CacheState.MISS;
	}

	// 참여자 캐시 상태 조회: 특정 대화방의 참여자 캐시 상태를 확인하여 Enum으로 반환
	private CacheState getParticipantsCacheState(UUID conversationId) {
		// 특정 대화방이 빈 방일 경우
		String emptyKey = String.format(EMPTY_CONVERSATION_PARTICIPANTS_KEY, conversationId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(emptyKey))) {
			return CacheState.EMPTY;
		}

		// 특정 대화방이 존재할 경우
		String key = String.format(CONVERSATION_PARTICIPANTS_KEY, conversationId);
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
			return CacheState.EXISTS;
		}

		return CacheState.MISS;
	}

	// 대화방 Key 생성: 순서 무관
	private String generateMappingKey(UUID u1, UUID u2) {
		String id1 = u1.toString();
		String id2 = u2.toString();
		return id1.compareTo(id2) < 0
			? String.format(CONVERSATION_MAPPING_KEY, id1, id2)
			: String.format(CONVERSATION_MAPPING_KEY, id2, id1);
	}

	// 빈 대화방 Key 생성: 순서 무관
	private String generateEmptyMappingKey(UUID u1, UUID u2) {
		String id1 = u1.toString();
		String id2 = u2.toString();
		return id1.compareTo(id2) < 0
			? String.format(EMPTY_CONVERSATION_MAPPING_KEY, id1, id2)
			: String.format(EMPTY_CONVERSATION_MAPPING_KEY, id2, id1);
	}
}
