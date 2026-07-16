package com.team04.mopl.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

class AuthSessionDataTest {

	@ParameterizedTest(name = "{0}이 null이면 생성에 실패한다")
	@MethodSource("missingRequiredFields")
	@DisplayName("필수값이 없으면 생성에 실패한다")
	void constructor_throwAuthException_whenRequiredValueIsNull(
		String fieldName,
		UUID userId,
		UUID sessionId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant updatedAt
	) {
		// given
		Instant lastRefreshedAt = null;

		// when & then
		assertThatThrownBy(() -> new AuthSessionData(
			userId,
			sessionId,
			refreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			lastRefreshedAt,
			updatedAt
		)).isInstanceOfSatisfying(AuthException.class, exception ->
			assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE)
		);
	}

	@Test
	@DisplayName("현재 시각이 refresh token 만료 시각 이상이면 만료된 세션이다")
	void isRefreshTokenExpired_returnTrue_whenNowIsAtExpiration() {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant refreshExpiresAt = updatedAt.plusSeconds(3600);
		AuthSessionData authSession = new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			updatedAt.plusSeconds(1800),
			refreshExpiresAt,
			null,
			updatedAt
		);

		// when
		boolean result = authSession.isRefreshTokenExpired(refreshExpiresAt);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("현재 시각이 refresh token 만료 직전이면 만료되지 않은 세션이다")
	void isRefreshTokenExpired_returnFalse_whenNowIsBeforeExpiration() {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant refreshExpiresAt = updatedAt.plusSeconds(3600);
		AuthSessionData authSession = new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			updatedAt.plusSeconds(1800),
			refreshExpiresAt,
			null,
			updatedAt
		);

		// when
		boolean result = authSession.isRefreshTokenExpired(refreshExpiresAt.minusNanos(1));

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("현재 시각이 refresh token 만료 직후이면 만료된 세션이다")
	void isRefreshTokenExpired_returnTrue_whenNowIsAfterExpiration() {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant refreshExpiresAt = updatedAt.plusSeconds(3600);
		AuthSessionData authSession = new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			updatedAt.plusSeconds(1800),
			refreshExpiresAt,
			null,
			updatedAt
		);

		// when
		boolean result = authSession.isRefreshTokenExpired(refreshExpiresAt.plusNanos(1));

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("현재 시각이 없으면 만료 여부 확인에 실패한다")
	void isRefreshTokenExpired_throwAuthException_whenNowIsNull() {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		AuthSessionData authSession = new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			updatedAt.plusSeconds(1800),
			updatedAt.plusSeconds(3600),
			null,
			updatedAt
		);

		// when & then
		assertThatThrownBy(() -> authSession.isRefreshTokenExpired(null))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE)
			);
	}

	@Test
	@DisplayName("access token 만료 시각이 갱신 시각보다 늦지 않으면 생성에 실패한다")
	void constructor_throwAuthException_whenAccessExpirationIsInvalid() {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");

		// when & then
		assertThatThrownBy(() -> new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			updatedAt,
			updatedAt.plusSeconds(3600),
			null,
			updatedAt
		)).isInstanceOfSatisfying(AuthException.class, exception ->
			assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRATION_INVALID)
		);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("refresh token 만료 시각이 access token 만료 시각보다 늦지 않으면 생성에 실패한다")
	void constructor_throwAuthException_whenRefreshExpirationIsInvalid(long refreshExpirationOffsetSeconds) {
		// given
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant accessExpiresAt = updatedAt.plusSeconds(1800);
		Instant refreshExpiresAt = accessExpiresAt.plusSeconds(refreshExpirationOffsetSeconds);

		// when & then
		assertThatThrownBy(() -> new AuthSessionData(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"refresh-token-hash",
			accessExpiresAt,
			refreshExpiresAt,
			null,
			updatedAt
		)).isInstanceOfSatisfying(AuthException.class, exception ->
			assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRATION_INVALID)
		);
	}

	private static Stream<Arguments> missingRequiredFields() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshTokenHash = "refresh-token-hash";
		Instant updatedAt = Instant.parse("2026-07-15T00:00:00Z");
		Instant accessExpiresAt = updatedAt.plusSeconds(1800);
		Instant refreshExpiresAt = updatedAt.plusSeconds(3600);

		return Stream.of(
			Arguments.of(
				"userId",
				null,
				sessionId,
				refreshTokenHash,
				accessExpiresAt,
				refreshExpiresAt,
				updatedAt
			),
			Arguments.of(
				"sessionId",
				userId,
				null,
				refreshTokenHash,
				accessExpiresAt,
				refreshExpiresAt,
				updatedAt
			),
			Arguments.of(
				"refreshTokenHash",
				userId,
				sessionId,
				null,
				accessExpiresAt,
				refreshExpiresAt,
				updatedAt
			),
			Arguments.of(
				"accessExpiresAt",
				userId,
				sessionId,
				refreshTokenHash,
				null,
				refreshExpiresAt,
				updatedAt
			),
			Arguments.of(
				"refreshExpiresAt",
				userId,
				sessionId,
				refreshTokenHash,
				accessExpiresAt,
				null,
				updatedAt
			),
			Arguments.of(
				"updatedAt",
				userId,
				sessionId,
				refreshTokenHash,
				accessExpiresAt,
				refreshExpiresAt,
				null
			)
		);
	}
}
