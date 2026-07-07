package com.team04.mopl.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.user.entity.User;

class TemporaryPasswordTest {

	@Test
	@DisplayName("정상 값으로 임시 비밀번호를 생성한다")
	void create_success_whenValuesValid() {
		// given
		User user = createUser();
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when
		TemporaryPassword temporaryPassword = TemporaryPassword.builder()
			.user(user)
			.passwordHash("password-hash")
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build();

		// then
		assertThat(temporaryPassword.getUser()).isEqualTo(user);
		assertThat(temporaryPassword.getPasswordHash()).isEqualTo("password-hash");
		assertThat(temporaryPassword.getCreatedAt()).isEqualTo(createdAt);
		assertThat(temporaryPassword.getExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	@DisplayName("현재 시각이 만료 시각 이전이면 만료되지 않은 상태다")
	void isExpired_returnFalse_whenNowBeforeExpiresAt() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when
		boolean result = temporaryPassword.isExpired(createdAt.plusSeconds(60));

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("현재 시각이 만료 시각과 같으면 만료된 상태다")
	void isExpired_returnTrue_whenNowEqualsExpiresAt() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when
		boolean result = temporaryPassword.isExpired(expiresAt);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("현재 시각이 만료 시각 이후면 만료된 상태다")
	void isExpired_returnTrue_whenNowAfterExpiresAt() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when
		boolean result = temporaryPassword.isExpired(expiresAt.plusSeconds(1));

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("임시 비밀번호를 재발급하면 해시값과 시각 정보가 갱신된다")
	void reissue_updateValues_whenValuesValid() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		Instant newCreatedAt = Instant.now().plusSeconds(10);
		Instant newExpiresAt = newCreatedAt.plusSeconds(180);

		// when
		temporaryPassword.reissue("new-password-hash", newCreatedAt, newExpiresAt);

		// then
		assertThat(temporaryPassword.getPasswordHash()).isEqualTo("new-password-hash");
		assertThat(temporaryPassword.getCreatedAt()).isEqualTo(newCreatedAt);
		assertThat(temporaryPassword.getExpiresAt()).isEqualTo(newExpiresAt);
	}

	@Test
	@DisplayName("사용자가 null이면 AuthException을 던진다")
	void create_throwAuthException_whenUserNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> TemporaryPassword.builder()
				.user(null)
				.passwordHash("password-hash")
				.createdAt(createdAt)
				.expiresAt(expiresAt)
				.build()
		);
	}

	@Test
	@DisplayName("비밀번호 해시가 null이면 AuthException을 던진다")
	void create_throwAuthException_whenPasswordHashNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> TemporaryPassword.builder()
				.user(createUser())
				.passwordHash(null)
				.createdAt(createdAt)
				.expiresAt(expiresAt)
				.build()
		);
	}

	@Test
	@DisplayName("비밀번호 해시가 blank이면 AuthException을 던진다")
	void create_throwAuthException_whenPasswordHashBlank() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> TemporaryPassword.builder()
				.user(createUser())
				.passwordHash(" ")
				.createdAt(createdAt)
				.expiresAt(expiresAt)
				.build()
		);
	}

	@Test
	@DisplayName("발급 시각이 null이면 AuthException을 던진다")
	void create_throwAuthException_whenCreatedAtNull() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> TemporaryPassword.builder()
				.user(createUser())
				.passwordHash("password-hash")
				.createdAt(null)
				.expiresAt(expiresAt)
				.build()
		);
	}

	@Test
	@DisplayName("만료 시각이 null이면 AuthException을 던진다")
	void create_throwAuthException_whenExpiresAtNull() {
		// given
		Instant createdAt = Instant.now();

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> TemporaryPassword.builder()
				.user(createUser())
				.passwordHash("password-hash")
				.createdAt(createdAt)
				.expiresAt(null)
				.build()
		);
	}

	@Test
	@DisplayName("만료 시각이 발급 시각보다 빠르거나 같으면 AuthException을 던진다")
	void create_throwAuthException_whenExpiresAtNotAfterCreatedAt() {
		// given
		Instant createdAt = Instant.now();

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRATION_INVALID,
			() -> TemporaryPassword.builder()
				.user(createUser())
				.passwordHash("password-hash")
				.createdAt(createdAt)
				.expiresAt(createdAt)
				.build()
		);
	}

	@Test
	@DisplayName("재발급 시 비밀번호 해시가 null이면 AuthException을 던진다")
	void reissue_throwAuthException_whenPasswordHashNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> temporaryPassword.reissue(null, createdAt, expiresAt)
		);
	}

	@Test
	@DisplayName("재발급 시 비밀번호 해시가 blank이면 AuthException을 던진다")
	void reissue_throwAuthException_whenPasswordHashBlank() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> temporaryPassword.reissue(" ", createdAt, expiresAt)
		);
	}

	@Test
	@DisplayName("재발급 시 발급 시각이 null이면 AuthException을 던진다")
	void reissue_throwAuthException_whenCreatedAtNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> temporaryPassword.reissue("new-password-hash", null, expiresAt)
		);
	}

	@Test
	@DisplayName("재발급 시 만료 시각이 null이면 AuthException을 던진다")
	void reissue_throwAuthException_whenExpiresAtNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> temporaryPassword.reissue("new-password-hash", createdAt, null)
		);
	}

	@Test
	@DisplayName("재발급 시 만료 시각이 발급 시각보다 빠르거나 같으면 AuthException을 던진다")
	void reissue_throwAuthException_whenExpiresAtNotAfterCreatedAt() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRATION_INVALID,
			() -> temporaryPassword.reissue("new-password-hash", createdAt, createdAt)
		);
	}

	@Test
	@DisplayName("만료 여부 확인 시 현재 시각이 null이면 AuthException을 던진다")
	void isExpired_throwAuthException_whenNowNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertAuthException(
			AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE,
			() -> temporaryPassword.isExpired(null)
		);
	}

	private TemporaryPassword createTemporaryPassword(Instant createdAt, Instant expiresAt) {
		return TemporaryPassword.builder()
			.user(createUser())
			.passwordHash("password-hash")
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build();
	}

	private User createUser() {
		User user = User.builder()
			.name("사용자")
			.email("user@example.com")
			.passwordHash("encoded-password")
			.build();

		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

		return user;
	}

	private static void assertAuthException(
		AuthErrorCode expectedErrorCode,
		ThrowingCallable callable
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception)
					.extracting("errorCode")
					.isEqualTo(expectedErrorCode)
			);
	}
}