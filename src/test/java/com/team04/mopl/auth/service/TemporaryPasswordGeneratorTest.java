package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

	private final TemporaryPasswordGenerator temporaryPasswordGenerator = new TemporaryPasswordGenerator();

	@Test
	@DisplayName("임시 비밀번호는 12자리로 생성된다")
	void generate_returnLength12() {
		// when
		String password = temporaryPasswordGenerator.generate();

		// then
		assertThat(password).hasSize(12);
	}

	@Test
	@DisplayName("임시 비밀번호는 영문자와 숫자로만 생성된다")
	void generate_returnAlphanumericPassword() {
		// when
		String password = temporaryPasswordGenerator.generate();

		// then
		assertThat(password).matches("^[A-Za-z0-9]{12}$");
	}

	@Test
	@DisplayName("임시 비밀번호는 빈 값이 아니다")
	void generate_returnNotBlankPassword() {
		// when
		String password = temporaryPasswordGenerator.generate();

		// then
		assertThat(password).isNotBlank();
	}
}