package com.team04.mopl.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtExpiredTokenValidatorTest {

	private final JwtExpiredTokenValidator validator = new JwtExpiredTokenValidator();

	@Test
	@DisplayName("만료 후 60초 이내이면 성공으로 처리한다")
	void validate_returnsSuccess_whenTokenExpiredWithinClockSkew() {
		// given
		Jwt jwt = createJwt(Instant.now().minusSeconds(30));

		// when
		OAuth2TokenValidatorResult result = validator.validate(jwt);

		// then
		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	@DisplayName("만료 후 60초를 초과하면 만료 에러를 반환한다")
	void validate_returnsExpiredError_whenTokenExpiredOverClockSkew() {
		// given
		Jwt jwt = createJwt(Instant.now().minusSeconds(90));

		// when
		OAuth2TokenValidatorResult result = validator.validate(jwt);

		// then
		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors())
			.extracting("errorCode")
			.containsExactly(JwtExpiredTokenValidator.EXPIRED_ACCESS_TOKEN_ERROR_CODE);
	}

	private Jwt createJwt(Instant expiresAt) {
		return Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject("subject")
			.issuedAt(Instant.now().minusSeconds(3600))
			.expiresAt(expiresAt)
			.build();
	}
}