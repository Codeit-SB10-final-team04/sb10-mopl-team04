package com.team04.mopl.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

	private static final String VALID_SECRET = "test-secret-key-must-be-at-least-32-bytes";

	@Test
	@DisplayName("JWT 설정 생성 시 secret이 32자 미만이면 예외가 발생한다")
	void constructor_throwsException_whenSecretIsTooShort() {
		// given
		String shortSecret = "short-secret";

		// when & then
		assertThatThrownBy(() -> new JwtProperties(
			shortSecret,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			false,
			"Lax"
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("JWT secret은 32자 이상이어야 합니다.");
	}

	@Test
	@DisplayName("JWT 설정 생성 시 선택값이 비어 있으면 기본값을 적용한다")
	void constructor_appliesDefaultValues_whenOptionalPropertiesAreInvalid() {
		// when
		JwtProperties jwtProperties = new JwtProperties(
			VALID_SECRET,
			"",
			0,
			1,
			"",
			false,
			""
		);

		// then
		assertThat(jwtProperties.issuer()).isEqualTo("mopl");
		assertThat(jwtProperties.accessTokenExpirationSeconds()).isEqualTo(1800);
		assertThat(jwtProperties.refreshTokenExpirationSeconds()).isEqualTo(1209600);
		assertThat(jwtProperties.refreshTokenCookieName()).isEqualTo("REFRESH_TOKEN");
		assertThat(jwtProperties.refreshTokenCookieSameSite()).isEqualTo("Lax");
	}

	@Test
	@DisplayName("Access Token 만료 시간을 Duration으로 변환한다")
	void accessTokenExpiration_returnsDuration_whenCalled() {
		// given
		JwtProperties jwtProperties = new JwtProperties(
			VALID_SECRET,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			false,
			"Lax"
		);

		// when
		Duration duration = jwtProperties.accessTokenExpiration();

		// then
		assertThat(duration).isEqualTo(Duration.ofSeconds(1800));
	}

	@Test
	@DisplayName("Refresh Token 만료 시간을 Duration으로 변환한다")
	void refreshTokenExpiration_returnsDuration_whenCalled() {
		// given
		JwtProperties jwtProperties = new JwtProperties(
			VALID_SECRET,
			"mopl",
			1800,
			1209600,
			"REFRESH_TOKEN",
			false,
			"Lax"
		);

		// when
		Duration duration = jwtProperties.refreshTokenExpiration();

		// then
		assertThat(duration).isEqualTo(Duration.ofSeconds(1209600));
	}
}