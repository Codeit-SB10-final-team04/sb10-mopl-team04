package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.common.storage.FileStorage;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserUpdateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.mapper.UserMapper;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserMapper userMapper;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private FileStorage fileStorage;

	@InjectMocks
	private UserService userService;

	@Test
	@DisplayName("이메일이 중복되지 않으면 비밀번호를 암호화하고 사용자를 등록한다")
	void create_createUser_whenEmailDoesNotExist() {
		// given
		UserCreateRequest request = new UserCreateRequest(
			"사용자",
			"test@test.com",
			"password123"
		);

		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-24T00:00:00Z");

		UserDto expectedResponse = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"사용자",
			null,
			UserRole.USER,
			false
		);

		given(userRepository.existsByEmail(request.email()))
			.willReturn(false);

		given(passwordEncoder.encode(request.password()))
			.willReturn("encoded-password");

		given(userRepository.saveAndFlush(any(User.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		given(userMapper.toDto(any(User.class)))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.create(request);

		// then
		assertThat(result).isEqualTo(expectedResponse);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());

		User savedUser = captor.getValue();

		assertThat(savedUser.getName()).isEqualTo("사용자");
		assertThat(savedUser.getEmail()).isEqualTo("test@test.com");
		assertThat(savedUser.getPasswordHashForAuthentication()).isEqualTo("encoded-password");
		assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
		assertThat(savedUser.isLocked()).isFalse();

		verify(userRepository).existsByEmail("test@test.com");
		verify(passwordEncoder).encode("password123");
		verify(userMapper).toDto(any(User.class));
	}

	@Test
	@DisplayName("이미 존재하는 이메일이면 사용자 등록에 실패한다")
	void create_throwUserException_whenEmailAlreadyExists() {
		// given
		UserCreateRequest request = new UserCreateRequest(
			"사용자",
			"test@test.com",
			"password123"
		);

		given(userRepository.existsByEmail(request.email()))
			.willReturn(true);

		// when
		ThrowingCallable action = () -> userService.create(request);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
				assertThat(exception.getDetails()).containsEntry("email", request.email());
			});

		verify(userRepository).existsByEmail("test@test.com");
		verify(userRepository, never()).saveAndFlush(any(User.class));
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("동시에 같은 이메일로 가입되어 DB unique 제약이 발생하면 사용자 등록에 실패한다")
	void create_throwUserException_whenEmailUniqueConstraintViolated() {
		// given
		UserCreateRequest request = new UserCreateRequest(
			"사용자",
			"test@test.com",
			"password123"
		);

		given(userRepository.existsByEmail(request.email()))
			.willReturn(false);

		given(passwordEncoder.encode(request.password()))
			.willReturn("encoded-password");

		given(userRepository.saveAndFlush(any(User.class)))
			.willThrow(new DataIntegrityViolationException("duplicate email"));

		// when
		ThrowingCallable action = () -> userService.create(request);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
				assertThat(exception.getDetails()).containsEntry("email", request.email());
			});

		verify(userRepository).existsByEmail("test@test.com");
		verify(passwordEncoder).encode("password123");
		verify(userRepository).saveAndFlush(any(User.class));
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("본인이 프로필 이름만 변경하면 사용자 정보를 반환한다")
	void updateProfile_updateName_whenOwnerRequestsNameOnly() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "기존이름", "http://localhost:8080/profile-images/old.png");
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		UserDto expectedResponse = new UserDto(
			userId,
			Instant.parse("2026-07-06T00:00:00Z"),
			"test@test.com",
			"새이름",
			"http://localhost:8080/profile-images/old.png",
			UserRole.USER,
			false
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.updateProfile(userId, request, null, userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);
		assertThat(user.getName()).isEqualTo("새이름");
		assertThat(user.getProfileImageUrl()).isEqualTo("http://localhost:8080/profile-images/old.png");

		verify(userRepository).findById(userId);
		verify(userMapper).toDto(user);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("본인이 프로필 이미지와 이름을 변경하면 새 이미지를 저장하고 기존 이미지를 삭제한다")
	void updateProfile_updateNameAndImage_whenOwnerRequestsImage() {
		// given
		UUID userId = UUID.randomUUID();
		String oldProfileImageUrl = "http://localhost:8080/profile-images/old.png";
		String newProfileImageUrl = "http://localhost:8080/profile-images/new.png";
		User user = createUser(userId, "기존이름", oldProfileImageUrl);
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			"image-data".getBytes()
		);
		UserDto expectedResponse = new UserDto(
			userId,
			Instant.parse("2026-07-06T00:00:00Z"),
			"test@test.com",
			"새이름",
			newProfileImageUrl,
			UserRole.USER,
			false
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(fileStorage.store(image, "profile-images"))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.updateProfile(userId, request, image, userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);
		assertThat(user.getName()).isEqualTo("새이름");
		assertThat(user.getProfileImageUrl()).isEqualTo(newProfileImageUrl);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, "profile-images");
		verify(fileStorage).delete(oldProfileImageUrl);
		verify(userMapper).toDto(user);
	}

	@Test
	@DisplayName("다른 사용자의 프로필 변경 요청이면 403 예외를 던진다")
	void updateProfile_throwUserException_whenCurrentUserIsNotOwner() {
		// given
		UUID userId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UserUpdateRequest request = new UserUpdateRequest("새이름");

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, null, currentUserId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_PROFILE_ACCESS_DENIED)
			);

		verifyNoInteractions(userRepository);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("프로필 변경 대상 사용자가 없으면 UserException을 던진다")
	void updateProfile_throwUserException_whenUserNotFound() {
		// given
		UUID userId = UUID.randomUUID();
		UserUpdateRequest request = new UserUpdateRequest("새이름");

		given(userRepository.findById(userId))
			.willReturn(Optional.empty());

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, null, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
				assertThat(exception.getDetails()).containsEntry("userId", userId);
			});

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("이미지를 저장한 뒤 프로필 변경에 실패하면 새 이미지를 삭제한다")
	void updateProfile_deleteNewImage_whenUpdateFailsAfterImageStored() {
		// given
		UUID userId = UUID.randomUUID();
		String newProfileImageUrl = "http://localhost:8080/profile-images/new.png";
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest("   ");
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			"image-data".getBytes()
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(fileStorage.store(image, "profile-images"))
			.willReturn(newProfileImageUrl);

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NAME_REQUIRED)
			);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, "profile-images");
		verify(fileStorage).delete(newProfileImageUrl);
		verifyNoInteractions(userMapper);
	}

	private User createUser(UUID userId, String name, String profileImageUrl) {
		User user = User.builder()
			.name(name)
			.email("test@test.com")
			.passwordHash("encoded-password")
			.profileImageUrl(profileImageUrl)
			.build();

		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}
}
