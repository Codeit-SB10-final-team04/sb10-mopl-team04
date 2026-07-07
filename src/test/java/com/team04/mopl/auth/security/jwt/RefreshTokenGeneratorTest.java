package com.team04.mopl.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

	private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();

	@Test
	@DisplayName("Refresh Token 문자열을 생성한다")
	void generate_returnsToken_whenCalled() {
		// when
		String refreshToken = refreshTokenGenerator.generate();

		// then
		assertThat(refreshToken).isNotBlank();
	}

	@Test
	@DisplayName("Refresh Token을 매번 다른 값으로 생성한다")
	void generate_returnsDifferentTokens_whenCalledMultipleTimes() {
		// when
		String firstToken = refreshTokenGenerator.generate();
		String secondToken = refreshTokenGenerator.generate();

		// then
		assertThat(firstToken).isNotEqualTo(secondToken);
	}
}