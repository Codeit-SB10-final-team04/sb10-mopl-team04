package com.team04.mopl.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

@ExtendWith(MockitoExtension.class)
class RedisAuthSessionStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private HashOperations<String, String, String> hashOperations;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Test
	@DisplayName("로그인 시 Redis 스크립트로 새 인증 세션을 저장한다")
	void replace_executeRedisScript_whenSessionIsIssued() {
		// given
		UUID userId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willReturn(1L);
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		store.replace(
			userId,
			UUID.randomUUID(),
			"refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			issuedAt
		);

		// then
		verify(redisTemplate).execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		);
	}

	@Test
	@DisplayName("사용자 ID로 Redis Hash 인증 세션을 조회한다")
	void findByUserId_returnAuthSession_whenSessionExists() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(hashOperations.entries(any())).willReturn(sessionValues(userId, sessionId, updatedAt));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		Optional<AuthSessionData> result = store.findByUserId(userId);

		// then
		assertThat(result).hasValueSatisfying(session -> {
			assertThat(session.userId()).isEqualTo(userId);
			assertThat(session.sessionId()).isEqualTo(sessionId);
			assertThat(session.refreshTokenHash()).isEqualTo("refresh-token-hash");
		});
	}

	@Test
	@DisplayName("sessionId와 refresh token hash가 모두 존재하면 활성 세션이다")
	void isActive_returnTrue_whenSessionFieldsAreValid() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(hashOperations.multiGet(any(), anyList()))
			.willReturn(List.of(sessionId.toString(), "refresh-token-hash"));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		boolean result = store.isActive(userId, sessionId);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("refresh token 역인덱스와 사용자 세션이 일치하면 인증 세션을 반환한다")
	void findByRefreshTokenHash_returnAuthSession_whenIndexMatchesSession() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.opsForValue()).willReturn(valueOperations);
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(valueOperations.get(any())).willReturn(userId.toString());
		given(hashOperations.entries(any())).willReturn(sessionValues(userId, sessionId, updatedAt));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		Optional<AuthSessionData> result = store.findByRefreshTokenHash("refresh-token-hash");

		// then
		assertThat(result).hasValueSatisfying(session ->
			assertThat(session.sessionId()).isEqualTo(sessionId)
		);
	}

	@Test
	@DisplayName("현재 세션과 refresh token이 일치하면 원자 갱신에 성공한다")
	void refresh_returnTrue_whenRedisScriptSucceeds() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willReturn(1L);
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		boolean result = store.refresh(
			userId,
			sessionId,
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("Redis 연결에 실패하면 인증 서비스 오류로 변환한다")
	void findByUserId_throwAuthException_whenRedisConnectionFails() {
		// given
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(hashOperations.entries(any()))
			.willThrow(new RedisConnectionFailureException("connection failed"));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.findByUserId(UUID.randomUUID()))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_SERVICE_ERROR)
			);
	}

	private Map<String, String> sessionValues(UUID userId, UUID sessionId, Instant updatedAt) {
		return Map.of(
			"userId", userId.toString(),
			"sessionId", sessionId.toString(),
			"refreshTokenHash", "refresh-token-hash",
			"accessExpiresAt", Long.toString(updatedAt.plusSeconds(1800).toEpochMilli()),
			"refreshExpiresAt", Long.toString(updatedAt.plusSeconds(1209600).toEpochMilli()),
			"lastRefreshedAt", "",
			"updatedAt", Long.toString(updatedAt.toEpochMilli())
		);
	}

	private static final class RedisScriptMatcher {

		private RedisScriptMatcher() {
		}

		@SuppressWarnings("unchecked")
		private static RedisScript<Long> anyLongScript() {
			return any(RedisScript.class);
		}
	}
}
