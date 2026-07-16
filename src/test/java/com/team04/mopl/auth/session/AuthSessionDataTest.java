package com.team04.mopl.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

class AuthSessionDataTest {

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
}
