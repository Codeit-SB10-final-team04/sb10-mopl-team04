package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

	private static final String EMAIL = "user@example.com";

	@Mock
	private UserRepository userRepository;

	@Mock
	private AuthSessionStore authSessionStore;

	@InjectMocks
	private UserAdminService userAdminService;

	@Test
	@DisplayName("사용자 권한을 USER에서 ADMIN으로 변경하면 인증 세션을 삭제한다")
	void updateRole_changeRoleAndDeleteSession_whenRoleChangedToAdmin() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, UserRole.USER);
		UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		// when
		userAdminService.updateRole(userId, request);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
		verify(authSessionStore).deleteByUserId(userId);
	}

	@Test
	@DisplayName("사용자 권한을 ADMIN에서 USER로 변경하면 인증 세션을 삭제한다")
	void updateRole_changeRoleAndDeleteSession_whenRoleChangedToUser() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, UserRole.ADMIN);
		UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.USER);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		// when
		userAdminService.updateRole(userId, request);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		verify(authSessionStore).deleteByUserId(userId);
	}

	@Test
	@DisplayName("같은 권한으로 수정 요청하면 권한을 변경하지 않고 인증 세션도 삭제하지 않는다")
	void updateRole_doNothing_whenRoleNotChanged() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, UserRole.USER);
		UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.USER);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		// when
		userAdminService.updateRole(userId, request);

		// then
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		verify(authSessionStore, never()).deleteByUserId(userId);
	}

	@Test
	@DisplayName("사용자가 존재하지 않으면 UserException을 던지고 인증 세션을 삭제하지 않는다")
	void updateRole_throwUserException_whenUserNotFound() {
		// given
		UUID userId = UUID.randomUUID();
		UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> userAdminService.updateRole(userId, request))
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND)
			);

		verify(authSessionStore, never()).deleteByUserId(userId);
	}

	@Test
	@DisplayName("권한이 null이면 UserException을 던지고 인증 세션을 삭제하지 않는다")
	void updateRole_throwUserException_whenRoleIsNull() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, UserRole.USER);
		UserRoleUpdateRequest request = new UserRoleUpdateRequest(null);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		// when, then
		assertThatThrownBy(() -> userAdminService.updateRole(userId, request))
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_ROLE_REQUIRED)
			);

		verify(authSessionStore, never()).deleteByUserId(userId);
	}

	private User createUser(UUID userId, UserRole role) {
		User user = User.builder()
			.name("사용자")
			.email(EMAIL)
			.passwordHash("encoded-password")
			.role(role)
			.locked(false)
			.build();

		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}
}
