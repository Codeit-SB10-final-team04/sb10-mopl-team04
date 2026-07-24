package com.team04.mopl.conversation.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.team04.mopl.common.enums.CacheState;

@ExtendWith(MockitoExtension.class)
class ConversationRedisStoreTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private SetOperations<String, String> setOperations;

	@InjectMocks
	private ConversationRedisStore conversationRedisStore;

	/*
	=========================
	   대화 추가
	=========================
	 */
	@Test
	@DisplayName("성공: 대화 추가 시 Lua 스크립트가 실행되고 TTL 30일이 지정된다.")
	void addConversation_Success() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
			.willReturn(1L);

		// when
		conversationRedisStore.addConversation(user1, user2, conversationId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString());
	}

	/*
	=========================
	   참여자 목록 추가
	=========================
	 */
	@Test
	@DisplayName("성공: 참여자 목록 추가 시 Lua 스크립트가 실행되고 TTL 30일이 지정된다.")
	void addParticipants_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		Set<UUID> participantIds = Set.of(UUID.randomUUID(), UUID.randomUUID());

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
			.willReturn(1L);

		// when
		conversationRedisStore.addParticipants(conversationId, participantIds);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
	}

	/*
	=========================
	   대화 ID 단건 조회
	=========================
	 */
	@Test
	@DisplayName("성공: CacheState가 EXISTS면 대화방 ID를 정상 반환한다.")
	void getConversationId_Exists_Success() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		UUID expectedConversationId = UUID.randomUUID();

		String id1 = user1.toString();
		String id2 = user2.toString();
		String key = id1.compareTo(id2) < 0
			? String.format("conv:mapping:%s:%s", id1, id2)
			: String.format("conv:mapping:%s:%s", id2, id1);
		String emptyKey = id1.compareTo(id2) < 0
			? String.format("conv:mapping:empty:%s:%s", id1, id2)
			: String.format("conv:mapping:empty:%s:%s", id2, id1);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(true);

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get(key)).willReturn(expectedConversationId.toString());

		// when
		UUID result = conversationRedisStore.getConversationId(user1, user2);

		// then
		assertThat(result).isEqualTo(expectedConversationId);
	}

	@Test
	@DisplayName("성공: CacheState가 EMPTY면 EMPTY_MARKER를 반환한다.")
	void getConversationId_Empty_ReturnsEmptyMarker() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		String id1 = user1.toString();
		String id2 = user2.toString();
		String emptyKey = id1.compareTo(id2) < 0
			? String.format("conv:mapping:empty:%s:%s", id1, id2)
			: String.format("conv:mapping:empty:%s:%s", id2, id1);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		UUID result = conversationRedisStore.getConversationId(user1, user2);

		// then
		assertThat(result).isEqualTo(ConversationRedisStore.EMPTY_MARKER);
		verify(stringRedisTemplate, never()).opsForValue();
	}

	@Test
	@DisplayName("성공: CacheState가 MISS면 null을 반환한다.")
	void getConversationId_Miss_ReturnsNull() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		String id1 = user1.toString();
		String id2 = user2.toString();
		String key = id1.compareTo(id2) < 0
			? String.format("conv:mapping:%s:%s", id1, id2)
			: String.format("conv:mapping:%s:%s", id2, id1);
		String emptyKey = id1.compareTo(id2) < 0
			? String.format("conv:mapping:empty:%s:%s", id1, id2)
			: String.format("conv:mapping:empty:%s:%s", id2, id1);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(false);

		// when
		UUID result = conversationRedisStore.getConversationId(user1, user2);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForValue();
	}

	/*
	=========================
	   대화방 전체 참여자 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: CacheState가 EXISTS면 참여자 목록을 Set<UUID>로 정상 반환한다.")
	void getParticipants_Exists_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID participant1 = UUID.randomUUID();
		UUID participant2 = UUID.randomUUID();

		String emptyKey = String.format("conv:room:empty:%s:participants", conversationId);
		String key = String.format("conv:room:%s:participants", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(true);

		Set<String> members = Set.of(participant1.toString(), participant2.toString());
		given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
		given(setOperations.members(key)).willReturn(members);

		// when
		Set<UUID> result = conversationRedisStore.getParticipants(conversationId);

		// then
		assertThat(result).containsExactlyInAnyOrder(participant1, participant2);
	}

	@Test
	@DisplayName("성공: CacheState가 EXISTS지만 반환된 members가 비어있으면 null을 반환한다.")
	void getParticipants_ExistsButEmpty_ReturnsNull() {
		// given
		UUID conversationId = UUID.randomUUID();

		String emptyKey = String.format("conv:room:empty:%s:participants", conversationId);
		String key = String.format("conv:room:%s:participants", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(true);

		given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
		given(setOperations.members(key)).willReturn(Collections.emptySet());

		// when
		Set<UUID> result = conversationRedisStore.getParticipants(conversationId);

		// then
		assertThat(result).isNull();
	}

	@Test
	@DisplayName("성공: CacheState가 EMPTY면 빈 Set을 반환한다.")
	void getParticipants_Empty_ReturnsEmptySet() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("conv:room:empty:%s:participants", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		Set<UUID> result = conversationRedisStore.getParticipants(conversationId);

		// then
		assertThat(result).isEmpty();
		verify(stringRedisTemplate, never()).opsForSet();
	}

	@Test
	@DisplayName("성공: CacheState가 MISS면 null을 반환한다.")
	void getParticipants_Miss_ReturnsNull() {
		// given
		UUID conversationId = UUID.randomUUID();
		String emptyKey = String.format("conv:room:empty:%s:participants", conversationId);
		String key = String.format("conv:room:%s:participants", conversationId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(key)).willReturn(false);

		// when
		Set<UUID> result = conversationRedisStore.getParticipants(conversationId);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForSet();
	}

	/*
	=========================
	   대화 백필 초기화
	=========================
	 */
	@Test
	@DisplayName("성공: 대화 백필 시 conversationId가 존재하면 addConversation을 수행하여 저장한다.")
	void initConversationMapping_Success() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
			.willReturn(1L);

		// when
		conversationRedisStore.initConversationMapping(user1, user2, conversationId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString());
	}

	@Test
	@DisplayName("성공: 대화 백필 시 conversationId가 null이면 10분짜리 네거티브 캐시(empty 마커)를 설정한다.")
	void initConversationMapping_NullId_SetsNegativeCache() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		conversationRedisStore.initConversationMapping(user1, user2, null);

		// then
		verify(valueOperations, times(1)).set(contains("empty"), eq("1"), eq(Duration.ofMinutes(10)));
		verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
	}

	/*
	=========================
	   참여자 백필 초기화
	=========================
	 */
	@Test
	@DisplayName("성공: 참여자 백필 시 participantIds가 존재하면 addParticipants를 수행하여 저장한다.")
	void initParticipants_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		Set<UUID> participantIds = Set.of(UUID.randomUUID());

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
			.willReturn(1L);

		// when
		conversationRedisStore.initParticipants(conversationId, participantIds);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
	}

	@Test
	@DisplayName("성공: 참여자 백필 시 participantIds가 비어있으면 10분짜리 네거티브 캐시(empty 마커)를 설정한다.")
	void initParticipants_EmptySet_SetsNegativeCache() {
		// given
		UUID conversationId = UUID.randomUUID();
		Set<UUID> emptySet = Collections.emptySet();

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		conversationRedisStore.initParticipants(conversationId, emptySet);

		// then
		verify(valueOperations, times(1)).set(contains("empty"), eq("1"), eq(Duration.ofMinutes(10)));
		verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
	}

	/*
	=========================
	   캐시 상태 조회
	=========================
	 */
	@Test
	@DisplayName("성공: getMappingCacheState 호출 시 빈 대화방 마커가 있으면 EMPTY 상태를 반환한다.")
	void getMappingCacheState_Empty_ReturnsEmpty() {
		// given
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		String id1 = user1.toString();
		String id2 = user2.toString();
		String emptyKey = id1.compareTo(id2) < 0
			? String.format("conv:mapping:empty:%s:%s", id1, id2)
			: String.format("conv:mapping:empty:%s:%s", id2, id1);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		CacheState state = conversationRedisStore.getMappingCacheState(user1, user2);

		// then
		assertThat(state).isEqualTo(CacheState.EMPTY);
	}
}
