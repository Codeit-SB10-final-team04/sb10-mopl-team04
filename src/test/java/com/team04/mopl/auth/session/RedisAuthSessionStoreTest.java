package com.team04.mopl.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.realtime.AuthSessionChangePublisher;

@ExtendWith(MockitoExtension.class)
class RedisAuthSessionStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private HashOperations<String, String, String> hashOperations;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private AuthSessionChangePublisher authSessionChangePublisher;

	@Test
	@DisplayName("인증 세션을 교체한 뒤 실시간 연결 정리 이벤트를 발행한다")
	void replace_publishesSessionChange_whenRedisScriptSucceeds() {
		// given
		UUID userId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willReturn(1L);
		RedisAuthSessionStore store = new RedisAuthSessionStore(
			redisTemplate,
			authSessionChangePublisher
		);

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
		verify(authSessionChangePublisher).publish(userId);
	}

	@Test
	@DisplayName("로그인 시 Redis 스크립트로 새 인증 세션을 저장한다")
	void replace_executeRedisScript_whenSessionIsIssued() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant accessExpiresAt = issuedAt.plusSeconds(1800);
		Instant refreshExpiresAt = issuedAt.plusSeconds(1209600);
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
			sessionId,
			"refresh-token-hash",
			accessExpiresAt,
			refreshExpiresAt,
			issuedAt
		);

		// then
		ArgumentCaptor<RedisScript<Long>> scriptCaptor = RedisScriptMatcher.longScriptCaptor();
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(redisTemplate).execute(
			scriptCaptor.capture(),
			eq(List.of(
				"mopl:{auth-session}:session:" + userId,
				"mopl:{auth-session}:session:" + userId + ":missing-refresh",
				"mopl:{auth-session}:refresh:refresh-token-hash"
			)),
			argumentsCaptor.capture()
		);
		assertThat(scriptCaptor.getValue().getScriptAsString())
			.isEqualTo(readScript("redis/auth-session/replace.lua"));
		assertThat(argumentsCaptor.getValue()).containsExactly(
			"",
			userId.toString(),
			sessionId.toString(),
			"refresh-token-hash",
			Long.toString(accessExpiresAt.toEpochMilli()),
			Long.toString(refreshExpiresAt.toEpochMilli()),
			Long.toString(issuedAt.toEpochMilli())
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
	@DisplayName("refresh token 역인덱스와 사용자 세션이 불일치하면 역인덱스를 삭제한다")
	void findByRefreshTokenHash_deleteIndexAndReturnEmpty_whenIndexDoesNotMatchSession() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String staleRefreshTokenHash = "stale-refresh-token-hash";
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.opsForValue()).willReturn(valueOperations);
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(valueOperations.get(any())).willReturn(userId.toString());
		given(hashOperations.entries(any())).willReturn(sessionValues(userId, sessionId, updatedAt));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		Optional<AuthSessionData> result = store.findByRefreshTokenHash(staleRefreshTokenHash);

		// then
		assertThat(result).isEmpty();
		verify(redisTemplate).delete("mopl:{auth-session}:refresh:" + staleRefreshTokenHash);
	}

	@Test
	@DisplayName("현재 세션과 refresh token이 일치하면 원자 갱신에 성공한다")
	void refresh_returnTrue_whenRedisScriptSucceeds() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant accessExpiresAt = refreshedAt.plusSeconds(1800);
		Instant refreshExpiresAt = refreshedAt.plusSeconds(1209600);
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
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<RedisScript<Long>> scriptCaptor = RedisScriptMatcher.longScriptCaptor();
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(redisTemplate).execute(
			scriptCaptor.capture(),
			eq(List.of(
				"mopl:{auth-session}:session:" + userId,
				"mopl:{auth-session}:refresh:current-refresh-token-hash",
				"mopl:{auth-session}:refresh:new-refresh-token-hash"
			)),
			argumentsCaptor.capture()
		);
		assertThat(scriptCaptor.getValue().getScriptAsString())
			.isEqualTo(readScript("redis/auth-session/refresh.lua"));
		assertThat(argumentsCaptor.getValue()).containsExactly(
			userId.toString(),
			sessionId.toString(),
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			Long.toString(accessExpiresAt.toEpochMilli()),
			Long.toString(refreshExpiresAt.toEpochMilli()),
			Long.toString(refreshedAt.toEpochMilli())
		);
	}

	@Test
	@DisplayName("현재 세션과 refresh token이 불일치하면 원자 갱신에 실패한다")
	void refresh_returnFalse_whenRedisScriptReturnsZero() {
		// given
		Instant refreshedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willReturn(0L);
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when
		boolean result = store.refresh(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("로그인 세션 식별자가 없으면 세션 교체에 실패한다")
	void replace_throwNullPointerException_whenSessionIdIsNull() {
		// given
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.replace(
			UUID.randomUUID(),
			null,
			"refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			issuedAt
		)).isInstanceOf(NullPointerException.class);

		verifyNoInteractions(redisTemplate);
	}

	@Test
	@DisplayName("활성 세션 식별자가 없으면 활성 여부 확인에 실패한다")
	void isActive_throwNullPointerException_whenSessionIdIsNull() {
		// given
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.isActive(UUID.randomUUID(), null))
			.isInstanceOf(NullPointerException.class);

		verifyNoInteractions(redisTemplate);
	}

	@Test
	@DisplayName("refresh token hash가 공백이면 세션 조회에 실패한다")
	void findByRefreshTokenHash_throwIllegalArgumentException_whenHashIsBlank() {
		// given
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.findByRefreshTokenHash(" "))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(redisTemplate);
	}

	@Test
	@DisplayName("갱신 시각이 없으면 refresh token 갱신에 실패한다")
	void refresh_throwNullPointerException_whenRefreshedAtIsNull() {
		// given
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.refresh(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			null
		)).isInstanceOf(NullPointerException.class);

		verifyNoInteractions(redisTemplate);
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

	@Test
	@DisplayName("로그인 세션 교체 중 Redis 연결에 실패하면 인증 서비스 오류로 변환한다")
	void replace_throwAuthException_whenRedisConnectionFails() {
		// given
		Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willThrow(new RedisConnectionFailureException("connection failed"));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.replace(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			issuedAt
		)).isInstanceOfSatisfying(AuthException.class, exception ->
			assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_SERVICE_ERROR)
		);
	}

	@Test
	@DisplayName("refresh token 갱신 중 Redis 연결에 실패하면 인증 서비스 오류로 변환한다")
	void refresh_throwAuthException_whenRedisConnectionFails() {
		// given
		Instant refreshedAt = Instant.parse("2026-07-15T00:00:00Z");
		given(redisTemplate.execute(
			RedisScriptMatcher.anyLongScript(),
			anyList(),
			any(Object[].class)
		)).willThrow(new RedisConnectionFailureException("connection failed"));
		RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

		// when & then
		assertThatThrownBy(() -> store.refresh(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		)).isInstanceOfSatisfying(AuthException.class, exception ->
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

	private static String readScript(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static final class RedisScriptMatcher {

		private RedisScriptMatcher() {
		}

		@SuppressWarnings("unchecked")
		private static RedisScript<Long> anyLongScript() {
			return any(RedisScript.class);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static ArgumentCaptor<RedisScript<Long>> longScriptCaptor() {
			return ArgumentCaptor.forClass((Class)RedisScript.class);
		}
	}
}
