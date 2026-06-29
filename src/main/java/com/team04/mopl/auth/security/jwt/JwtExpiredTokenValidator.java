package com.team04.mopl.auth.security.jwt;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Access Token 만료 여부를 명확한 에러 코드로 검증하는 Validator
 */
public class JwtExpiredTokenValidator implements OAuth2TokenValidator<Jwt> {

	public static final String EXPIRED_ACCESS_TOKEN_ERROR_CODE = "expired_access_token";

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	// JWT의 expiresAt 값을 확인해 만료된 토큰이면 전용 에러 코드를 반환
	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		Instant expiresAt = token.getExpiresAt();

		if (expiresAt != null && expiresAt.plus(CLOCK_SKEW).isBefore(Instant.now())) {
			OAuth2Error error = new OAuth2Error(
				EXPIRED_ACCESS_TOKEN_ERROR_CODE,
				"Access token has expired.",
				null
			);

			return OAuth2TokenValidatorResult.failure(error);
		}

		return OAuth2TokenValidatorResult.success();
	}
}
