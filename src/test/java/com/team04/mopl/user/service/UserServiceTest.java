package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.auth.service.TemporaryPasswordService;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.request.ChangePasswordRequest;
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

	@Mock
	private TemporaryPasswordService temporaryPasswordService;

	@Mock
	private AuthSessionStore authSessionStore;

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
	@DisplayName("사용자가 존재하면 상세 정보를 반환한다")
	void findById_returnUser_whenUserExists() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "사용자", "http://localhost:8080/profile-images/profile.png");
		UserDto expectedResponse = new UserDto(
			userId,
			Instant.parse("2026-07-07T00:00:00Z"),
			"test@test.com",
			"사용자",
			"http://localhost:8080/profile-images/profile.png",
			UserRole.USER,
			false
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.findById(userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);

		verify(userRepository).findById(userId);
		verify(userMapper).toDto(user);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("상세 조회 대상 사용자가 없으면 UserException을 던진다")
	void findById_throwUserException_whenUserNotFound() {
		// given
		UUID userId = UUID.randomUUID();

		given(userRepository.findById(userId))
			.willReturn(Optional.empty());

		// when
		ThrowingCallable action = () -> userService.findById(userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
				assertThat(exception.getDetails()).containsEntry("userId", userId);
			});

		verify(userRepository).findById(userId);
		verifyNoInteractions(userMapper);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("본인이 비밀번호를 변경하면 비밀번호를 암호화하고 임시 비밀번호를 삭제한다")
	void updatePassword_updatePasswordHashAndDeleteTemporaryPassword_whenOwnerRequests() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "사용자", null);
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(passwordEncoder.encode(request.password()))
			.willReturn("new-encoded-password");

		// when
		userService.updatePassword(userId, request, userId);

		// then
		assertThat(user.getPasswordHashForAuthentication()).isEqualTo("new-encoded-password");

		verify(userRepository).findById(userId);
		verify(passwordEncoder).encode("newPassword123");
		verify(temporaryPasswordService).deleteTemporaryPassword(userId);
		verify(authSessionStore).deleteByUserId(userId);
		verifyNoInteractions(userMapper);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("다른 사용자의 비밀번호 변경 요청이면 403 예외를 던진다")
	void updatePassword_throwUserException_whenCurrentUserIsNotOwner() {
		// given
		UUID userId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");

		// when
		ThrowingCallable action = () -> userService.updatePassword(userId, request, currentUserId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_PASSWORD_ACCESS_DENIED);
				assertThat(exception.getDetails()).containsEntry("userId", userId);
				assertThat(exception.getDetails()).containsEntry("currentUserId", currentUserId.toString());
			});

		verifyNoInteractions(userRepository);
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(temporaryPasswordService);
		verifyNoInteractions(userMapper);
		verifyNoInteractions(fileStorage);
	}

	@Test
	@DisplayName("비밀번호 변경 대상 사용자가 없으면 UserException을 던진다")
	void updatePassword_throwUserException_whenUserNotFound() {
		// given
		UUID userId = UUID.randomUUID();
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");

		given(userRepository.findById(userId))
			.willReturn(Optional.empty());

		// when
		ThrowingCallable action = () -> userService.updatePassword(userId, request, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
				assertThat(exception.getDetails()).containsEntry("userId", userId);
			});

		verify(userRepository).findById(userId);
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(temporaryPasswordService);
		verifyNoInteractions(userMapper);
		verifyNoInteractions(fileStorage);
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
			validPngBytes()
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
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
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
		verify(fileStorage).store(image, UserService.PROFILE_IMAGE_DIRECTORY);
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
	@DisplayName("본인이 프로필 이미지만 변경하면 이름은 유지하고 이미지를 변경한다")
	void updateProfile_updateImageOnly_whenNameIsNull() {
		// given
		UUID userId = UUID.randomUUID();
		String oldProfileImageUrl = "http://localhost:8080/profile-images/old.png";
		String newProfileImageUrl = "http://localhost:8080/profile-images/new.png";
		User user = createUser(userId, "기존이름", oldProfileImageUrl);
		UserUpdateRequest request = new UserUpdateRequest(null);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			validPngBytes()
		);
		UserDto expectedResponse = new UserDto(
			userId,
			Instant.parse("2026-07-06T00:00:00Z"),
			"test@test.com",
			"기존이름",
			newProfileImageUrl,
			UserRole.USER,
			false
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.updateProfile(userId, request, image, userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);
		assertThat(user.getName()).isEqualTo("기존이름");
		assertThat(user.getProfileImageUrl()).isEqualTo(newProfileImageUrl);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, UserService.PROFILE_IMAGE_DIRECTORY);
		verify(fileStorage).delete(oldProfileImageUrl);
		verify(userMapper).toDto(user);
	}

	@Test
	@DisplayName("기존 프로필 이미지가 없으면 새 이미지만 저장하고 기존 이미지 삭제를 건너뛴다")
	void updateProfile_skipOldImageDelete_whenOldProfileImageDoesNotExist() {
		// given
		UUID userId = UUID.randomUUID();
		String newProfileImageUrl = "http://localhost:8080/profile-images/new.png";
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest(null);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			validPngBytes()
		);
		UserDto expectedResponse = new UserDto(
			userId,
			Instant.parse("2026-07-06T00:00:00Z"),
			"test@test.com",
			"기존이름",
			newProfileImageUrl,
			UserRole.USER,
			false
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		// when
		UserDto result = userService.updateProfile(userId, request, image, userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);
		assertThat(user.getProfileImageUrl()).isEqualTo(newProfileImageUrl);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, UserService.PROFILE_IMAGE_DIRECTORY);
		verify(fileStorage, never()).delete(any());
		verify(userMapper).toDto(user);
	}

	@Test
	@DisplayName("트랜잭션 커밋 이후 기존 프로필 이미지를 삭제한다")
	void updateProfile_deleteOldImageAfterCommit_whenTransactionCommits() {
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
			validPngBytes()
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
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);

		TransactionSynchronizationManager.initSynchronization();

		try {
			// when
			UserDto result = userService.updateProfile(userId, request, image, userId);

			// then
			assertThat(result).isEqualTo(expectedResponse);
			verify(fileStorage, never()).delete(oldProfileImageUrl);

			TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);

			verify(fileStorage).delete(oldProfileImageUrl);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("기존 프로필 이미지 삭제에 실패해도 프로필 변경은 성공한다")
	void updateProfile_returnResponse_whenOldImageDeleteFails() {
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
			validPngBytes()
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
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willReturn(expectedResponse);
		willThrow(new RuntimeException("delete failed"))
			.given(fileStorage)
			.delete(oldProfileImageUrl);

		// when
		UserDto result = userService.updateProfile(userId, request, image, userId);

		// then
		assertThat(result).isEqualTo(expectedResponse);
		assertThat(user.getProfileImageUrl()).isEqualTo(newProfileImageUrl);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, UserService.PROFILE_IMAGE_DIRECTORY);
		verify(fileStorage).delete(oldProfileImageUrl);
		verify(userMapper).toDto(user);
	}

	@Test
	@DisplayName("이름이 올바르지 않으면 프로필 이미지를 저장하지 않는다")
	void updateProfile_throwUserExceptionAndDoesNotStoreImage_whenNameIsBlank() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest("   ");
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			validPngBytes()
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NAME_REQUIRED)
			);

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("허용하지 않는 이미지 형식이면 프로필 이미지를 저장하지 않는다")
	void updateProfile_throwUserException_whenProfileImageContentTypeIsInvalid() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest(null);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.txt",
			"text/plain",
			"image-data".getBytes()
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_PROFILE_IMAGE)
			);

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("실제 이미지로 디코딩할 수 없으면 프로필 이미지를 저장하지 않는다")
	void updateProfile_throwUserException_whenProfileImageIsNotReadable() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "Existing", null);
		UserUpdateRequest request = new UserUpdateRequest(null);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			"not-an-image".getBytes()
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_PROFILE_IMAGE);
				assertThat(exception.getDetails())
					.containsEntry("reason", "지원하지 않거나 손상된 이미지 파일입니다.");
			});

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("이미지 파일 스트림을 읽을 수 없으면 프로필 이미지를 저장하지 않는다")
	void updateProfile_throwUserException_whenProfileImageInputStreamFails() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "Existing", null);
		UserUpdateRequest request = new UserUpdateRequest(null);
		MultipartFile image = mock(MultipartFile.class);

		given(image.isEmpty())
			.willReturn(false);
		given(image.getSize())
			.willReturn(1024L);
		given(image.getContentType())
			.willReturn("image/png");
		given(image.getOriginalFilename())
			.willReturn("broken.png");
		given(image.getInputStream())
			.willThrow(new IOException("stream failed"));
		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_PROFILE_IMAGE);
				assertThat(exception.getDetails())
					.containsEntry("reason", "이미지 파일을 읽을 수 없습니다.");
			});

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("이미지 크기가 최대 크기를 초과하면 프로필 이미지를 저장하지 않는다")
	void updateProfile_throwUserException_whenProfileImageSizeIsTooLarge() {
		// given
		UUID userId = UUID.randomUUID();
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest(null);
		byte[] largeImage = new byte[10 * 1024 * 1024 + 1];
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			largeImage
		);

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_PROFILE_IMAGE)
			);

		verify(userRepository).findById(userId);
		verifyNoInteractions(fileStorage);
		verifyNoInteractions(userMapper);
	}

	@Test
	@DisplayName("새 이미지 보상 삭제에 실패해도 원래 예외를 유지한다")
	void updateProfile_throwOriginalException_whenNewImageDeleteFails() {
		// given
		UUID userId = UUID.randomUUID();
		String newProfileImageUrl = "http://localhost:8080/profile-images/new.png";
		User user = createUser(userId, "기존이름", null);
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			"image/png",
			validPngBytes()
		);
		RuntimeException originalException = new RuntimeException("mapping failed");

		given(userRepository.findById(userId))
			.willReturn(Optional.of(user));
		given(fileStorage.store(image, UserService.PROFILE_IMAGE_DIRECTORY))
			.willReturn(newProfileImageUrl);
		given(userMapper.toDto(user))
			.willThrow(originalException);
		willThrow(new RuntimeException("delete failed"))
			.given(fileStorage)
			.delete(newProfileImageUrl);

		// when
		ThrowingCallable action = () -> userService.updateProfile(userId, request, image, userId);

		// then
		assertThatThrownBy(action)
			.isSameAs(originalException);

		verify(userRepository).findById(userId);
		verify(fileStorage).store(image, UserService.PROFILE_IMAGE_DIRECTORY);
		verify(fileStorage).delete(newProfileImageUrl);
		verify(userMapper).toDto(user);
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

	// 테스트용 1x1 PNG 바이트 생성
	private byte[] validPngBytes() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		try {
			ImageIO.write(image, "png", outputStream);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}

		return outputStream.toByteArray();
	}
}
