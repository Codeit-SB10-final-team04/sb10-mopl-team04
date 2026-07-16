package com.team04.mopl.directmessage.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisStoreTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private DirectMessageRedisStore directMessageRedisStore;

	/*
	=========================
	   DM 추가
	=========================
	 */
	@Test
	@DisplayName("성공: DM 추가 시 Lua 스크립트가 실행되고 TTL 7일이 지정된다.")
	void addDirectMessage_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessageDto dto = mock(DirectMessageDto.class);

		given(dto.createdAt()).willReturn(Instant.now());
		given(dto.id()).willReturn(UUID.randomUUID());

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
			.willReturn(1L);

		// when
		directMessageRedisStore.addDirectMessage(conversationId, dto);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any());
		verify(stringRedisTemplate, times(1)).expire(anyString(), eq(Duration.ofDays(7)));
	}

	/*
	=========================
	   안 읽음 카운트 증가/감소
	=========================
	 */
	@Test
	@DisplayName("성공: 특정 사용자의 특정 대화방 및 전체 안 읽음 DM 개수가 증가한다.")
	void incrementUnreadCount_Success() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.increment(anyString())).willReturn(1L);

		// when
		directMessageRedisStore.incrementUnreadCount(receiverId, conversationId);

		// then
		verify(valueOperations, times(2)).increment(anyString());
	}

	@Test
	@DisplayName("성공: 안 읽음 개수 감소 시 Lua 스크립트를 사용하여 원자적으로 감소시킨다.")
	void decrementUnreadCount_Success() {
		// given
		UUID userId = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
			.willReturn(1L);

		// when
		directMessageRedisStore.decrementUnreadCount(userId, conversationId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList());
	}

	/*
	=========================
	   DM ID 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: CacheState가 EXISTS면 reverseRangeByScore를 통해 DM ID 목록을 정상 반환한다.")
	void getDirectMessageIdsByCursor_Exists_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		long cursorTimestamp = System.currentTimeMillis();
		int limit = 10;

		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		String key = String.format("dm:room:%s:messages", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(true);

		Set<String> expectedSet = Set.of(UUID.randomUUID().toString());
		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.reverseRangeByScore(key, 0, cursorTimestamp, 0, limit))
			.willReturn(expectedSet);

		// when
		Set<String> result = directMessageRedisStore.getDirectMessageIdsByCursor(conversationId, cursorTimestamp,
			limit);

		// then
		assertThat(result).isEqualTo(expectedSet);
	}

	@Test
	@DisplayName("성공: CacheState가 EMPTY면 빈 Set을 반환한다.")
	void getDirectMessageIdsByCursor_Empty_ReturnsEmptySet() {
		// given
		UUID conversationId = UUID.randomUUID();
		long cursorTimestamp = System.currentTimeMillis();
		int limit = 10;

		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		Set<String> result = directMessageRedisStore.getDirectMessageIdsByCursor(conversationId, cursorTimestamp,
			limit);

		// then
		assertThat(result).isEmpty();
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	@Test
	@DisplayName("성공: CacheState가 MISS면 null을 반환한다.")
	void getDirectMessageIdsByCursor_Miss_ReturnsNull() {
		// given
		UUID conversationId = UUID.randomUUID();
		long cursorTimestamp = System.currentTimeMillis();
		int limit = 10;

		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		String key = String.format("dm:room:%s:messages", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(false);

		// when
		Set<String> result = directMessageRedisStore.getDirectMessageIdsByCursor(conversationId, cursorTimestamp,
			limit);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	/*
	=========================
	   전체 메시지 수 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 글로벌 안 읽음 카운트 값이 존재하면 long으로 변환하여 반환한다.")
	void getGlobalUnreadCount_HasValue_Success() {
		// given
		UUID userId = UUID.randomUUID();
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(anyString())).willReturn("5");

		// when
		Long result = directMessageRedisStore.getGlobalUnreadCount(userId);

		// then
		assertThat(result).isEqualTo(5L);
	}

	@Test
	@DisplayName("성공: 글로벌 안 읽음 카운트 값이 없으면(null) 0L을 반환한다.")
	void getGlobalUnreadCount_NullValue_ReturnsZero() {
		// given
		UUID userId = UUID.randomUUID();
		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(anyString())).willReturn(null);

		// when
		Long result = directMessageRedisStore.getGlobalUnreadCount(userId);

		// then
		assertThat(result).isEqualTo(0L);
	}

	/*
	=========================
	   방별 전체 메시지 수 조회
	=========================
	 */
	@Test
	@DisplayName("성공: CacheState가 EXISTS면 zCard를 통해 대화방 메시지 수를 정상 반환한다.")
	void getRoomMessageTotalCount_Exists_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		long expectedCount = 20L;

		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		String key = String.format("dm:room:%s:messages", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(true);

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.zCard(key)).willReturn(expectedCount);

		// when
		Long result = directMessageRedisStore.getRoomMessageTotalCount(conversationId);

		// then
		assertThat(result).isEqualTo(expectedCount);
	}

	@Test
	@DisplayName("성공: CacheState가 EMPTY면 0L을 반환한다.")
	void getRoomMessageTotalCount_Empty_ReturnsZero() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("dm:room:empty:%s", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		Long result = directMessageRedisStore.getRoomMessageTotalCount(conversationId);

		// then
		assertThat(result).isEqualTo(0L);
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	@Test
	@DisplayName("성공: CacheState가 MISS면 null을 반환한다.")
	void getRoomMessageTotalCount_Miss_ReturnsNull() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		String key = String.format("dm:room:%s:messages", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(false);

		// when
		Long result = directMessageRedisStore.getRoomMessageTotalCount(conversationId);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	/*
	=========================
	   캐시 존재 확인
	=========================
	 */
	@Test
	@DisplayName("성공: CacheState가 MISS가 아니면(EXISTS 또는 EMPTY) true를 반환한다.")
	void hasRoomMessages_NotMiss_ReturnsTrue() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("dm:room:empty:%s", conversationId);

		// EMPTY 상태로 유도
		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		boolean result = directMessageRedisStore.hasRoomMessages(conversationId);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("성공: CacheState가 MISS면 false를 반환한다.")
	void hasRoomMessages_Miss_ReturnsFalse() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("dm:room:empty:%s", conversationId);
		String key = String.format("dm:room:%s:messages", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(false);

		// when
		boolean result = directMessageRedisStore.hasRoomMessages(conversationId);

		// then
		assertThat(result).isFalse();
	}

	/*
	=========================
	   DM 백필 초기화
	=========================
	 */
	@Test
	@DisplayName("성공: DM 백필 시 messages 리스트가 존재하면 벌크 ZADD(add) 연산을 수행하고 7일 TTL을 세팅한다.")
	void initDirectMessages_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessage msg = mock(DirectMessage.class);
		given(msg.getId()).willReturn(UUID.randomUUID());
		given(msg.getCreatedAt()).willReturn(Instant.now());

		List<DirectMessage> messages = List.of(msg);

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.add(anyString(), anySet())).willReturn(1L);

		// when
		directMessageRedisStore.initDirectMessages(conversationId, messages);

		// then
		verify(zSetOperations, times(1)).add(anyString(), anySet());
		verify(stringRedisTemplate, times(1)).expire(anyString(), eq(Duration.ofDays(7)));
	}

	@Test
	@DisplayName("성공: DM 백필 시 messages 리스트가 비어있으면 10분짜리 네거티브 캐시(empty 마커)를 설정한다.")
	void initDirectMessages_EmptyList_SetsNegativeCache() {
		// given
		UUID conversationId = UUID.randomUUID();
		List<DirectMessage> emptyList = Collections.emptyList();

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		directMessageRedisStore.initDirectMessages(conversationId, emptyList);

		// then
		verify(valueOperations, times(1)).set(contains("empty"), eq("1"), any(Duration.class));
		verify(stringRedisTemplate, never()).opsForZSet();
	}
}