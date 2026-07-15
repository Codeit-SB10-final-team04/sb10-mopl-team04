package com.team04.mopl.follow.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class FollowRedisStoreTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@InjectMocks
	private FollowRedisStore followRedisStore;

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

	@Test
	@DisplayName("성공: Redis에 팔로워 리스트가 존재하면 전체 갯수(ZCard)를 정상 반환한다.")
	void getFollowerCount_HasCount_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		long expectedCount = 42L;

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.zCard(anyString())).willReturn(expectedCount);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(expectedCount);
	}

	@Test
	@DisplayName("성공: Redis에 팔로워 리스트가 없거나 조회에 실패하면 0L을 반환한다.")
	void getFollowerCount_NullCount_ReturnsZero() {
		// given
		UUID followeeId = UUID.randomUUID();

		given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
		given(zSetOperations.zCard(anyString())).willReturn(null);

		// when
		Long result = followRedisStore.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(0L);
	}

	@Test
	@DisplayName("성공: 팔로우 취소 시 Redis Pipelining을 사용하여 원자적으로 두 ZSET에서 요소를 삭제한다.")
	void removeFollow_Pipelined_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();

		// 파이프라이닝
		ArgumentCaptor<RedisCallback> callbackCaptor = ArgumentCaptor.forClass(RedisCallback.class);

		given(stringRedisTemplate.executePipelined(callbackCaptor.capture())).willReturn(List.of());

		RedisConnection mockConnection = mock(RedisConnection.class);
		RedisZSetCommands mockZSetCommands = mock(RedisZSetCommands.class);
		given(mockConnection.zSetCommands()).willReturn(mockZSetCommands);

		// when
		followRedisStore.removeFollow(followeeId, followerId);

		// then
		RedisCallback capturedCallback = callbackCaptor.getValue();
		capturedCallback.doInRedis(mockConnection);

		verify(mockZSetCommands, times(2)).zRem(any(byte[].class), any(byte[].class));
	}
}