package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.response.CursorResponseUserDto;
import com.team04.mopl.user.dto.response.UserCursorPage;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;
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
	@DisplayName("관리자 사용자 목록 조회 결과를 커서 응답으로 반환한다")
	void findUsers_returnCursorResponse_whenUsersExist() {
		// given
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();
		Instant createdAt1 = Instant.parse("2026-07-01T00:00:00Z");
		Instant createdAt2 = Instant.parse("2026-07-02T00:00:00Z");
		UserPageRequest request = new UserPageRequest(
			"test",
			UserRole.USER,
			false,
			null,
			null,
			2,
			SortDirection.ASCENDING,
			UserSortBy.name
		);
		UserDto user1 = new UserDto(
			userId1,
			createdAt1,
			"alpha@test.com",
			"Alpha",
			null,
			UserRole.USER,
			false
		);
		UserDto user2 = new UserDto(
			userId2,
			createdAt2,
			"bravo@test.com",
			"Bravo",
			null,
			UserRole.USER,
			false
		);

		when(userRepository.findUsers(request))
			.thenReturn(new UserCursorPage(List.of(user1, user2), true, 3L));

		// when
		CursorResponseUserDto result = userAdminService.findUsers(request);

		// then
		assertThat(result.data()).containsExactly(user1, user2);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.nextCursor()).isEqualTo("Bravo");
		assertThat(result.nextIdAfter()).isEqualTo(userId2);
		assertThat(result.totalCount()).isEqualTo(3L);
		assertThat(result.sortBy()).isEqualTo("name");
		assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING);
		verify(userRepository).findUsers(request);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 결과가 마지막 페이지이면 다음 커서를 반환하지 않는다")
	void findUsers_returnCursorResponseWithoutNextCursor_whenLastPage() {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-02T00:00:00Z");
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			null,
			null,
			20,
			SortDirection.DESCENDING,
			UserSortBy.createdAt
		);
		UserDto user = new UserDto(
			userId,
			createdAt,
			"admin@test.com",
			"관리자",
			"https://example.com/admin.png",
			UserRole.ADMIN,
			true
		);

		when(userRepository.findUsers(request))
			.thenReturn(new UserCursorPage(List.of(user), false, 1L));

		// when
		CursorResponseUserDto result = userAdminService.findUsers(request);

		// then
		assertThat(result.data()).containsExactly(user);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
		assertThat(result.nextIdAfter()).isNull();
		assertThat(result.totalCount()).isEqualTo(1L);
		assertThat(result.sortBy()).isEqualTo("createdAt");
		assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
		verify(userRepository).findUsers(request);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 중 저장소 예외가 발생하면 예외를 그대로 전달한다")
	void findUsers_throwUserException_whenRepositoryThrowsException() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"invalid-cursor",
			UUID.randomUUID(),
			20,
			SortDirection.ASCENDING,
			UserSortBy.createdAt
		);
		UserException exception = new UserException(UserErrorCode.USER_INVALID_CURSOR);

		when(userRepository.findUsers(request)).thenThrow(exception);

		// when, then
		assertThatThrownBy(() -> userAdminService.findUsers(request))
			.isSameAs(exception);

		verify(userRepository).findUsers(request);
	}

	@ParameterizedTest
	@EnumSource(UserSortBy.class)
	@DisplayName("관리자 사용자 목록 조회 결과의 정렬 기준별 다음 커서를 반환한다")
	void findUsers_returnNextCursor_bySortBy(UserSortBy sortBy) {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-02T00:00:00Z");
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			null,
			null,
			1,
			SortDirection.ASCENDING,
			sortBy
		);
		UserDto user = new UserDto(
			userId,
			createdAt,
			"cursor@test.com",
			"커서사용자",
			null,
			UserRole.ADMIN,
			true
		);

		when(userRepository.findUsers(request))
			.thenReturn(new UserCursorPage(List.of(user), true, 2L));

		// when
		CursorResponseUserDto result = userAdminService.findUsers(request);

		// then
		assertThat(result.nextCursor()).isEqualTo(expectedNextCursor(sortBy, user));
		assertThat(result.nextIdAfter()).isEqualTo(userId);
		verify(userRepository).findUsers(request);
	}

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

	private String expectedNextCursor(UserSortBy sortBy, UserDto user) {
		return switch (sortBy) {
			case name -> user.name();
			case email -> user.email();
			case createdAt -> user.createdAt().toString();
			case isLocked -> user.locked().toString();
			case role -> user.role().toString();
		};
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
