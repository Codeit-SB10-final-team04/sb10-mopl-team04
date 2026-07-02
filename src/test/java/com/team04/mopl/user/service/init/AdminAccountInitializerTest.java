package com.team04.mopl.user.service.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

	private static final String ADMIN_EMAIL = "admin@mopl.local";
	private static final String ADMIN_PASSWORD = "admin1234!";
	private static final String ENCODED_PASSWORD = "encoded-admin-password";
	private static final String ADMIN_NAME = "관리자";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthSessionStore authSessionStore;

	@Mock
	private ApplicationArguments applicationArguments;

	@InjectMocks
	private AdminAccountInitializer adminAccountInitializer;

	@Test
	@DisplayName("어드민 초기화가 비활성화되어 있으면 아무 작업도 하지 않는다")
	void run_doNothing_whenAdminInitializerDisabled() {
		// given
		setProperties(false, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		verifyNoInteractions(userRepository);
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("어드민 계정이 없으면 새 ADMIN 계정을 생성한다")
	void run_createAdminAccount_whenAdminUserNotExists() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
		when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn(ENCODED_PASSWORD);

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

		verify(userRepository).saveAndFlush(userCaptor.capture());

		User savedUser = userCaptor.getValue();

		assertThat(savedUser.getEmail()).isEqualTo(ADMIN_EMAIL);
		assertThat(savedUser.getName()).isEqualTo(ADMIN_NAME);
		assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(savedUser.isLocked()).isFalse();
		assertThat(savedUser.getPasswordHashForAuthentication()).isEqualTo(ENCODED_PASSWORD);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("어드민 계정 생성 중 이메일 중복이 발생하면 동시 생성된 계정을 사용한다")
	void run_useConcurrentCreatedAdminAccount_whenEmailUniqueConstraintViolated() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		User admin = createUser(
			UUID.randomUUID(),
			ADMIN_EMAIL,
			UserRole.ADMIN,
			false,
			ENCODED_PASSWORD
		);

		when(userRepository.findByEmail(ADMIN_EMAIL))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(admin));
		when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn(ENCODED_PASSWORD);
		when(userRepository.saveAndFlush(any(User.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate admin email"));

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		verify(userRepository, times(2)).findByEmail(ADMIN_EMAIL);
		verify(userRepository).saveAndFlush(any(User.class));
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("이미 ADMIN 계정이 존재하면 새 계정을 생성하지 않는다")
	void run_doNotCreateAdminAccount_whenAdminUserAlreadyExists() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		User admin = createUser(
			UUID.randomUUID(),
			ADMIN_EMAIL,
			UserRole.ADMIN,
			false,
			ENCODED_PASSWORD
		);

		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(admin.isLocked()).isFalse();
		assertThat(admin.getPasswordHashForAuthentication()).isEqualTo(ENCODED_PASSWORD);

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("기존 계정이 USER이면 ADMIN으로 보정하고 인증 세션을 삭제한다")
	void run_updateRoleAndDeleteSession_whenExistingUserIsNotAdmin() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		User user = createUser(
			UUID.randomUUID(),
			ADMIN_EMAIL,
			UserRole.USER,
			false,
			ENCODED_PASSWORD
		);

		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(user));

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(user.isLocked()).isFalse();
		assertThat(user.getPasswordHashForAuthentication()).isEqualTo(ENCODED_PASSWORD);

		verify(userRepository).saveAndFlush(user);
		verify(authSessionStore).deleteByUserId(user.getId());
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("기존 어드민 계정이 잠겨 있으면 잠금을 해제하고 인증 세션을 삭제한다")
	void run_unlockAndDeleteSession_whenExistingAdminUserIsLocked() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		User user = createUser(
			UUID.randomUUID(),
			ADMIN_EMAIL,
			UserRole.ADMIN,
			true,
			ENCODED_PASSWORD
		);

		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(user));

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(user.isLocked()).isFalse();
		assertThat(user.getPasswordHashForAuthentication()).isEqualTo(ENCODED_PASSWORD);

		verify(userRepository).saveAndFlush(user);
		verify(authSessionStore).deleteByUserId(user.getId());
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("기존 어드민 계정이 비밀번호 로그인을 지원하지 않으면 비밀번호를 보정하고 인증 세션을 삭제한다")
	void run_updatePasswordAndDeleteSession_whenExistingAdminUserHasNoPassword() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);

		User user = createUser(
			UUID.randomUUID(),
			ADMIN_EMAIL,
			UserRole.ADMIN,
			false,
			null
		);

		when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn(ENCODED_PASSWORD);

		// when
		adminAccountInitializer.run(applicationArguments);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(user.isLocked()).isFalse();
		assertThat(user.getPasswordHashForAuthentication()).isEqualTo(ENCODED_PASSWORD);

		verify(userRepository).saveAndFlush(user);
		verify(authSessionStore).deleteByUserId(user.getId());
		verify(passwordEncoder).encode(ADMIN_PASSWORD);
	}

	@Test
	@DisplayName("어드민 이메일 설정이 없으면 예외를 던진다")
	void run_throwIllegalStateException_whenAdminEmailMissing() {
		// given
		setProperties(true, "", ADMIN_PASSWORD, ADMIN_NAME);

		// when, then
		assertThatThrownBy(() -> adminAccountInitializer.run(applicationArguments))
			.isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(userRepository);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("어드민 비밀번호 설정이 없으면 예외를 던진다")
	void run_throwIllegalStateException_whenAdminPasswordMissing() {
		// given
		setProperties(true, ADMIN_EMAIL, "", ADMIN_NAME);

		// when, then
		assertThatThrownBy(() -> adminAccountInitializer.run(applicationArguments))
			.isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(userRepository);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("어드민 이름 설정이 없으면 예외를 던진다")
	void run_throwIllegalStateException_whenAdminNameMissing() {
		// given
		setProperties(true, ADMIN_EMAIL, ADMIN_PASSWORD, "");

		// when, then
		assertThatThrownBy(() -> adminAccountInitializer.run(applicationArguments))
			.isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(userRepository);
		verifyNoInteractions(passwordEncoder);
	}

	private void setProperties(
		boolean enabled,
		String adminEmail,
		String adminPassword,
		String adminName
	) {
		ReflectionTestUtils.setField(adminAccountInitializer, "enabled", enabled);
		ReflectionTestUtils.setField(adminAccountInitializer, "adminEmail", adminEmail);
		ReflectionTestUtils.setField(adminAccountInitializer, "adminPassword", adminPassword);
		ReflectionTestUtils.setField(adminAccountInitializer, "adminName", adminName);
	}

	private User createUser(
		UUID userId,
		String email,
		UserRole role,
		boolean locked,
		String passwordHash
	) {
		User user = User.builder()
			.name("사용자")
			.email(email)
			.passwordHash(passwordHash)
			.role(role)
			.locked(locked)
			.build();

		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}
}
