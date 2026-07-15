package com.team04.mopl.follow.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
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
	@DisplayName("성공: 팔로우 추가 시 Lua 스크립트가 실행되고 1L이 반환되면 true를 반환한다.")
	void addFollow_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		// Lua 스크립트 실행 성공 결과 (1L)
		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.willReturn(1L);

		// when
		boolean result = followRedisStore.addFollow(followerId, followeeId);

		// then
		assertThat(result).isTrue();
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any(), any());
	}

	@Test
	@DisplayName("성공: 팔로우 추가 시 Lua 스크립트에 empty 마커 키가 정확히 전달되어 원자적 삭제를 유도한다.")
	void addFollow_DeletesEmptyMarker_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		// 예측되는 emptyKey 값
		String expectedEmptyKey = String.format("follow:followers:empty:%s", followeeId);

		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.willReturn(1L);

		// when
		boolean result = followRedisStore.addFollow(followerId, followeeId);

		// then
		assertThat(result).isTrue();

		// 💡 핵심 검증: Lua 스크립트를 실행할 때 KEYS 배열(List)의 3번째 인자로 emptyKey가 정확히 넘어가는지 확인
		verify(stringRedisTemplate, times(1)).execute(
			any(RedisScript.class),
			argThat(keys -> keys.size() == 3 && keys.get(2).equals(expectedEmptyKey)),
			anyString(),
			anyString(),
			anyString()
		);
	}

	@Test
	@DisplayName("실패: 팔로우 추가 시 이미 존재하거나 연산에 실패하여 0L이 반환되면 false를 반환한다.")
	void addFollow_AlreadyExists_ReturnsFalse() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		// Lua 스크립트 실행 실패 / 중복 결과 (0L)
		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.willReturn(0L);

		// when
		boolean result = followRedisStore.addFollow(followerId, followeeId);

		// then
		assertThat(result).isFalse();
	}

	/*
	============================
	   특정 사용자 팔로우 여부 조회
	============================
	 */
	@Test
	@DisplayName("성공: Redis ZSET에 스코어가 존재하면 팔로우 중인 것으로 판단(true)한다.")
	void isFollowing_True_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.score(anyString(), eq(followeeId.toString())))
			.willReturn(1623456789.0);

		// when
		boolean result = followRedisStore.isFollowing(followerId, followeeId);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("성공: Redis ZSET에 스코어가 없으면 팔로우하지 않는 것으로 판단(false)한다.")
	void isFollowing_False_Success() {
		// given
		UUID followerId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.score(anyString(), eq(followeeId.toString())))
			.willReturn(null);

		// when
		boolean result = followRedisStore.isFollowing(followerId, followeeId);

		// then
		assertThat(result).isFalse();
	}

	/*
	=============================
	   특정 사용자의 팔로우 수 조회
	=============================
	 */
	@Test
	@DisplayName("성공: Redis 키가 존재(hasKey=true)하면 zCard를 통해 팔로워 수를 정상 반환한다.")
	void getFollowerCount_HasCount_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		long expectedCount = 42L;

		String emptyKey = String.format("follow:followers:empty:%s", followeeId);
		String followersKey = String.format("follow:followers:%s", followeeId);

		given(stringRedisTemplate.hasKey(emptyKey)).willReturn(false);
		given(stringRedisTemplate.hasKey(followersKey)).willReturn(true);
		
		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.zCard(anyString())).willReturn(expectedCount);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(expectedCount);
	}

	@Test
	@DisplayName("성공: Redis 키가 존재하지 않으면(hasKey=false) Cache Miss로 간주하고 null을 반환한다.")
	void getFollowerCount_CacheMiss_ReturnsNull() {
		// given
		UUID followeeId = UUID.randomUUID();

		given(stringRedisTemplate.hasKey(anyString())).willReturn(false);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isNull();
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	@Test
	@DisplayName("성공: 네거티브 캐시(empty 마커)가 존재하면 바로 0L을 반환한다.")
	void getFollowerCount_NegativeCache_ReturnsZero() {
		// given
		UUID followeeId = UUID.randomUUID();

		// emptyKey가 존재함
		given(stringRedisTemplate.hasKey(contains("empty"))).willReturn(true);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(0L);
		verify(stringRedisTemplate, never()).opsForZSet();
	}

	@Test
	@DisplayName("성공: 팔로워 백필 시 followerIds 컬렉션이 존재하면 벌크 ZADD(add) 연산을 수행한다.")
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
		verify(valueOperations, times(1)).set(contains("empty"), eq("1"), any(Duration.class));
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

		// Lua 스크립트 실행 결과 모킹
		given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
			.willReturn(1L);

		// when
		followRedisStore.removeFollow(followeeId, followerId);

		// then
		verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any());
	}
}