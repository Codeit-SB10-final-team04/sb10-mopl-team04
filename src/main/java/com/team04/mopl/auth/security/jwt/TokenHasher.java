package com.team04.mopl.auth.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 토큰 원문을 해시값으로 변환하는 컴포넌트
 *
 * - Refresh Token을 SHA-256 해시로 변환
 * - 이후 Refresh Token 검증 시에도 같은 방식으로 해시한 뒤 저장된 값과 비교
 */
@Component
public class TokenHasher {

	private static final String HASH_ALGORITHM = "SHA-256";

	// refresh token 원문을 SHA-256 해시 후 Base64 문자열로 변환
	public String hash(String token) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
			byte[] tokenHash = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

			return Base64.getEncoder().encodeToString(tokenHash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}
}
