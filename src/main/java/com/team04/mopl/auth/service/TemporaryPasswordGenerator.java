package com.team04.mopl.auth.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 비밀번호 초기화에 사용할 임시 비밀번호 원문을 생성하는 컴포넌트
 *
 * - 생성된 원문 비밀번호는 사용자에게 이메일로 전송
 * - DB에는 PasswordEncoder로 암호화된 해시값만 저장
 */
@Component
public class TemporaryPasswordGenerator {

	private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final int PASSWORD_LENGTH = 12;

	private final SecureRandom secureRandom = new SecureRandom();

	// 지정된 문자 집합에서 랜덤 문자를 뽑아 12자리 임시 비밀번호를 생성
	public String generate() {
		StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);

		for (int index = 0; index < PASSWORD_LENGTH; index++) {
			int randomIndex = secureRandom.nextInt(CHARACTERS.length());
			builder.append(CHARACTERS.charAt(randomIndex));
		}

		return builder.toString();
	}
}
