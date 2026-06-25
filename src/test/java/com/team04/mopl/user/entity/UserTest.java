package com.team04.mopl.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

class UserTest {

	@Test
	@DisplayName("선택값이 null이면 기본값을 적용한다")
	void builder_applyDefaultValues_whenOptionalValuesAreNull() {
		// given

		// when
		User user = User.builder()
			.name("사용자")
			.email("test@test.com")
			.passwordHash("encoded-password")
			.build();

		// then
		assertThat(user.getName()).isEqualTo("사용자");
		assertThat(user.getEmail()).isEqualTo("test@test.com");
		assertThat(user.getEmailType()).isEqualTo(EmailType.REAL);
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.isLocked()).isFalse();
	}

	@Test
	@DisplayName("명시한 값으로 사용자를 생성한다")
	void builder_createUser_whenAllValuesAreProvided() {
		// given

		// when
		User user = User.builder()
			.name("관리자")
			.email("ADMIN@test.com")
			.emailType(EmailType.REAL)
			.passwordHash("encoded-password")
			.profileImageUrl("https://example.com/profile.png")
			.role(UserRole.ADMIN)
			.locked(true)
			.build();

		// then
		assertThat(user.getName()).isEqualTo("관리자");
		assertThat(user.getEmail()).isEqualTo("ADMIN@test.com");
		assertThat(user.getEmailType()).isEqualTo(EmailType.REAL);
		assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(user.isLocked()).isTrue();
	}

	@Test
	@DisplayName("이름이 null이면 사용자 생성에 실패한다")
	void builder_throwUserException_whenNameIsNull() {
		// given

		// when
		ThrowingCallable action = () -> User.builder()
			.name(null)
			.email("test@test.com")
			.passwordHash("encoded-password")
			.build();

		// then
		assertUserException(action, UserErrorCode.NAME_REQUIRED);
	}

	@Test
	@DisplayName("이름이 공백이면 사용자 생성에 실패한다")
	void builder_throwUserException_whenNameIsBlank() {
		// given

		// when
		ThrowingCallable action = () -> User.builder()
			.name("   ")
			.email("test@test.com")
			.passwordHash("encoded-password")
			.build();

		// then
		assertUserException(action, UserErrorCode.NAME_REQUIRED);
	}

	@Test
	@DisplayName("이메일이 null이면 사용자 생성에 실패한다")
	void builder_throwUserException_whenEmailIsNull() {
		// given

		// when
		ThrowingCallable action = () -> User.builder()
			.name("사용자")
			.email(null)
			.passwordHash("encoded-password")
			.build();

		// then
		assertUserException(action, UserErrorCode.EMAIL_REQUIRED);
	}

	@Test
	@DisplayName("이메일이 공백이면 사용자 생성에 실패한다")
	void builder_throwUserException_whenEmailIsBlank() {
		// given

		// when
		ThrowingCallable action = () -> User.builder()
			.name("사용자")
			.email("   ")
			.passwordHash("encoded-password")
			.build();

		// then
		assertUserException(action, UserErrorCode.EMAIL_REQUIRED);
	}

	@Test
	@DisplayName("이름을 변경한다")
	void updateName_updateName_whenValidInput() {
		// given
		User user = createUser();

		// when
		user.updateName("변경된 이름");

		// then
		assertThat(user.getName()).isEqualTo("변경된 이름");
	}

	@Test
	@DisplayName("변경할 이름이 공백이면 이름 변경에 실패한다")
	void updateName_throwUserException_whenNameIsBlank() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> user.updateName("   ");

		// then
		assertUserException(action, UserErrorCode.NAME_REQUIRED);
	}

	@Test
	@DisplayName("프로필 이미지 URL을 변경한다")
	void updateProfileImageUrl_updateProfileImageUrl_whenCalled() {
		// given
		User user = createUser();

		// when
		user.updateProfileImageUrl("https://example.com/new-profile.png");

		// then
		assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new-profile.png");
	}

	@Test
	@DisplayName("비밀번호 해시를 변경한다")
	void updatePasswordHash_updatePasswordHash_whenValidInput() {
		// given
		User user = createUser();

		// when
		user.updatePasswordHash("new-encoded-password");

		// then
		assertThat(user.isPasswordLoginSupported()).isTrue();
		assertThat(user.getPasswordHashForAuthentication()).isEqualTo("new-encoded-password");
	}

	@Test
	@DisplayName("비밀번호 해시가 공백이면 비밀번호 변경에 실패한다")
	void updatePasswordHash_throwUserException_whenPasswordHashIsBlank() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> user.updatePasswordHash("   ");

		// then
		assertUserException(action, UserErrorCode.PASSWORD_REQUIRED);
	}

	@Test
	@DisplayName("권한을 변경한다")
	void updateRole_updateRole_whenValidInput() {
		// given
		User user = createUser();

		// when
		user.updateRole(UserRole.ADMIN);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
	}

	@Test
	@DisplayName("권한이 null이면 권한 변경에 실패한다")
	void updateRole_throwUserException_whenRoleIsNull() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> user.updateRole(null);

		// then
		assertUserException(action, UserErrorCode.ROLE_REQUIRED);
	}

	@Test
	@DisplayName("계정 잠금 상태를 변경한다")
	void updateLocked_updateLocked_whenCalled() {
		// given
		User user = createUser();

		// when
		user.updateLocked(true);

		// then
		assertThat(user.isLocked()).isTrue();
	}

	@Test
	@DisplayName("권한이 ADMIN이면 관리자라고 판단한다")
	void isAdmin_returnTrue_whenRoleIsAdmin() {
		// given
		User user = User.builder()
			.name("관리자")
			.email("admin@test.com")
			.role(UserRole.ADMIN)
			.build();

		// when
		boolean result = user.isAdmin();

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("권한이 USER이면 관리자가 아니라고 판단한다")
	void isAdmin_returnFalse_whenRoleIsUser() {
		// given
		User user = createUser();

		// when
		boolean result = user.isAdmin();

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("비밀번호 해시가 있으면 비밀번호 로그인을 지원한다고 판단한다")
	void isPasswordLoginSupported_returnTrue_whenPasswordHashExists() {
		// given
		User user = createUser();

		// when
		boolean result = user.isPasswordLoginSupported();

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("비밀번호 해시가 null이면 비밀번호 로그인을 지원하지 않는다고 판단한다")
	void isPasswordLoginSupported_returnFalse_whenPasswordHashIsNull() {
		// given
		User user = User.builder()
			.name("소셜 사용자")
			.email("social@test.com")
			.passwordHash(null)
			.build();

		// when
		boolean result = user.isPasswordLoginSupported();

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("비밀번호 해시가 공백이면 비밀번호 로그인을 지원하지 않는다고 판단한다")
	void isPasswordLoginSupported_returnFalse_whenPasswordHashIsBlank() {
		// given
		User user = User.builder()
			.name("사용자")
			.email("blank@test.com")
			.passwordHash("   ")
			.build();

		// when
		boolean result = user.isPasswordLoginSupported();

		// then
		assertThat(result).isFalse();
	}

	private static void assertUserException(ThrowingCallable action, UserErrorCode expectedErrorCode) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
				assertThat(exception).hasMessage(expectedErrorCode.getMessage());
			});
	}

	private User createUser() {
		return User.builder()
			.name("사용자")
			.email("test@test.com")
			.passwordHash("encoded-password")
			.build();
	}
}
