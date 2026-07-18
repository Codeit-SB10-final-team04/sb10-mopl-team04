package com.team04.mopl.follow.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

@ExtendWith(MockitoExtension.class)
class FollowRedisStoreTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private FollowRedisStore followRedisStore;

	/*
	=========================
	   팔로우 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 팔로우 추가 시 Lua 스크립트가 실행되고 1L이 반환되며 TTL 7일이 지정된다.")
	void addFollow_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		// Lua 스크립트 실행 성공 결과 (1L)
		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.willReturn(1L);

		// when
		followRedisStore.addFollow(followerId, followeeId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any(), any());
	}

	@Test
	@DisplayName("성공: 팔로우 추가 시 Lua 스크립트에 empty 마커 키가 정확히 전달되어 원자적 삭제를 유도한다.")
	void addFollow_DeletesEmptyMarker_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		String expectedEmptyKey = String.format("follow:followers:empty:%s", followeeId);

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.willReturn(1L);

		// when
		followRedisStore.addFollow(followerId, followeeId);

		// then
		verify(stringRedisTemplate, times(1)).execute(
			any(RedisScript.class),
			argThat(keys -> keys.size() == 4 && keys.get(2).equals(expectedEmptyKey)),
			anyString(),
			anyString(),
			anyString()
		);
	}

	/*
	=============================
	   특정 사용자의 팔로우 수 조회
	=============================
	 */
	@Test
	@DisplayName("성공: CacheState가 EXISTS면 zCard를 통해 팔로워 수를 정상 반환한다.")
	void getFollowerCount_HasCount_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		long expectedCount = 42L;

		String emptyKey = String.format("follow:followers:empty:%s", followeeId);
		String followersKey = String.format("follow:followers:%s", followeeId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(followersKey)).willReturn(true);

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.zCard(followersKey)).willReturn(expectedCount);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(expectedCount);
	}

	@Test
	@DisplayName("성공: Redis 키가 모두 존재하지 않으면 CacheState.MISS로 간주하고 null을 반환한다.")
	void getFollowerCount_CacheMiss_ReturnsNull() {
		// given
		UUID followeeId = UUID.randomUUID();
		String emptyKey = String.format("follow:followers:empty:%s", followeeId);
		String followersKey = String.format("follow:followers:%s", followeeId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(followersKey)).willReturn(false);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	@Test
	@DisplayName("성공: CacheState가 EMPTY면 네거티브 캐시를 인지하고 바로 0L을 반환한다.")
	void getFollowerCount_NegativeCache_ReturnsZero() {
		// given
		UUID followeeId = UUID.randomUUID();
		String emptyKey = String.format("follow:followers:empty:%s", followeeId);

		// emptyKey가 존재함
		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(true);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(0L);
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	/*
	==================
	   팔로워 백필 초기화
	==================
	 */
	@Test
	@DisplayName("성공: 팔로워 백필 시 followerIds 컬렉션이 존재하면 벌크 ZADD(add) 연산을 수행하고 7일 TTL을 세팅한다.")
	void initFollowers_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		Set<UUID> followerIds = Set.of(UUID.randomUUID(), UUID.randomUUID());

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.add(anyString(), anySet())).willReturn(2L);

		// when
		followRedisStore.initFollowers(followeeId, followerIds);

		// then
		verify(zSetOperations, times(1)).add(anyString(), anySet());
	}

	@Test
	@DisplayName("성공: 팔로워 백필 시 followerIds가 비어있으면 10분짜리 네거티브 캐시(empty 마커)를 설정한다.")
	void initFollowers_EmptyList_SetsNegativeCache() {
		// given
		UUID followeeId = UUID.randomUUID();
		Set<UUID> emptyList = Set.of();

		given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		followRedisStore.initFollowers(followeeId, emptyList);

		// then
		verify(valueOperations, times(1)).set(contains("empty"), eq("1"), eq(10L), eq(TimeUnit.MINUTES));
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	/*
	==================
	   팔로우 취소
	==================
	 */
	@Test
	@DisplayName("성공: 팔로우 취소 시 Lua 스크립트를 사용하여 원자적으로 두 ZSET에서 요소를 삭제한다.")
	void removeFollow_LuaScript_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
			.willReturn(1L);

		// when
		followRedisStore.removeFollow(followeeId, followerId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any());
	}
}