package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.team04.mopl.user.dto.request.UserCreateRequest;
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
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
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
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
				assertThat(exception.getDetails()).containsEntry("email", request.email());
			});

		verify(userRepository).existsByEmail("test@test.com");
		verify(passwordEncoder).encode("password123");
		verify(userRepository).saveAndFlush(any(User.class));
		verifyNoInteractions(userMapper);
	}
}