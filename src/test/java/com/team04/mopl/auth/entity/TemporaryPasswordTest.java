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
	@DisplayName("사용자가 null이면 예외를 던진다")
	void create_throwException_whenUserNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when, then
		assertThatThrownBy(() -> TemporaryPassword.builder()
			.user(null)
			.passwordHash("password-hash")
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build())
			.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("비밀번호 해시가 blank이면 예외를 던진다")
	void create_throwException_whenPasswordHashBlank() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);

		// when, then
		assertThatThrownBy(() -> TemporaryPassword.builder()
			.user(createUser())
			.passwordHash(" ")
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build())
			.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("만료 시각이 발급 시각보다 빠르거나 같으면 예외를 던진다")
	void create_throwException_whenExpiresAtNotAfterCreatedAt() {
		// given
		Instant createdAt = Instant.now();

		// when, then
		assertThatThrownBy(() -> TemporaryPassword.builder()
			.user(createUser())
			.passwordHash("password-hash")
			.createdAt(createdAt)
			.expiresAt(createdAt)
			.build())
			.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("만료 여부 확인 시 현재 시각이 null이면 예외를 던진다")
	void isExpired_throwException_whenNowNull() {
		// given
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(180);
		TemporaryPassword temporaryPassword = createTemporaryPassword(createdAt, expiresAt);

		// when, then
		assertThatThrownBy(() -> temporaryPassword.isExpired(null))
			.isInstanceOf(AuthException.class);
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
}