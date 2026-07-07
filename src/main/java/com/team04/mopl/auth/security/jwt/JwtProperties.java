package com.team04.mopl.auth.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mopl.jwt 설정값을 바인딩하는 설정 클래스
 *
 * - JWT secret, issuer, 토큰 만료 시간, Refresh Token 쿠키 설정을 관리
 * - 필수 설정값 검증과 일부 기본값 보정도 함께 수행
 */
@ConfigurationProperties(prefix = "mopl.jwt")
public record JwtProperties(
	String secret,
	String issuer,
	long accessTokenExpirationSeconds,
	long refreshTokenExpirationSeconds,
	String refreshTokenCookieName,
	boolean refreshTokenCookieSecure,
	String refreshTokenCookieSameSite
) {

	// JWT 설정값을 검증하고 비어 있는 선택값에는 기본값 적용
	public JwtProperties {
		if (secret == null || secret.length() < 32) {
			throw new IllegalArgumentException("JWT secret은 32자 이상이어야 합니다.");
		}

		if (issuer == null || issuer.isBlank()) {
			issuer = "mopl";
		}

		if (accessTokenExpirationSeconds <= 0) {
			accessTokenExpirationSeconds = 1800;
		}

		if (refreshTokenExpirationSeconds <= accessTokenExpirationSeconds) {
			refreshTokenExpirationSeconds = 1209600;
		}

		if (refreshTokenCookieName == null || refreshTokenCookieName.isBlank()) {
			refreshTokenCookieName = "REFRESH_TOKEN";
		}

		if (refreshTokenCookieSameSite == null || refreshTokenCookieSameSite.isBlank()) {
			refreshTokenCookieSameSite = "Lax";
		}
	}

	// access token 만료 시간을 Duration으로 변환
	public Duration accessTokenExpiration() {
		return Duration.ofSeconds(accessTokenExpirationSeconds);
	}

	// refresh token 만료 시간을 Duration으로 변환
	public Duration refreshTokenExpiration() {
		return Duration.ofSeconds(refreshTokenExpirationSeconds);
	}
}