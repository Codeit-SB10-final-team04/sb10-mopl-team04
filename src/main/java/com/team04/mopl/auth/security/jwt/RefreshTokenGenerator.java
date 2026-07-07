package com.team04.mopl.auth.security.jwt;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Refresh Token 원문을 생성하는 컴포넌트
 *
 * SecureRandom으로 충분히 긴 난수 바이트를 만들고,
 * URL-safe Base64 문자열로 인코딩해 Refresh Token으로 사용
 */
@Component
public class RefreshTokenGenerator {

	private static final int TOKEN_BYTE_SIZE = 64;

	private final SecureRandom secureRandom = new SecureRandom();

	// 새로운 refresh token 원문 생성
	public String generate() {
		byte[] tokenBytes = new byte[TOKEN_BYTE_SIZE];
		// secureRandom(암호학적 난수 생성기)를 활용하여 바이트 배열 생성
		secureRandom.nextBytes(tokenBytes);

		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(tokenBytes);
	}
}
