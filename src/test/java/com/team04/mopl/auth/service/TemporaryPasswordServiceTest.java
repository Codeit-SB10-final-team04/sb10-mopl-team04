package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.entity.TemporaryPassword;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
import com.team04.mopl.auth.service.event.TemporaryPasswordIssuedEvent;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordServiceTest {

	private static final String EMAIL = "user@example.com";
	private static final String TEMPORARY_PASSWORD = "TempPassword1";
	private static final String TEMPORARY_PASSWORD_HASH = "encoded-temporary-password";

	@Mock
	private UserRepository userRepository;

	@Mock
	private TemporaryPasswordRepository temporaryPasswordRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TemporaryPasswordGenerator temporaryPasswordGenerator;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private TemporaryPasswordService temporaryPasswordService;

	@Test
	@DisplayName("존재하는 이메일이면 임시 비밀번호를 저장하고 메일 발송 이벤트를 발행한다")
	void resetPassword_saveTemporaryPasswordAndPublishEvent_whenUserExists() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(temporaryPasswordGenerator.generate()).thenReturn(TEMPORARY_PASSWORD);
		when(passwordEncoder.encode(TEMPORARY_PASSWORD)).thenReturn(TEMPORARY_PASSWORD_HASH);
		when(temporaryPasswordRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());

		// when
		temporaryPasswordService.resetPassword(EMAIL);

		// then
		ArgumentCaptor<TemporaryPassword> passwordCaptor = ArgumentCaptor.forClass(TemporaryPassword.class);
		ArgumentCaptor<TemporaryPasswordIssuedEvent> eventCaptor =
			ArgumentCaptor.forClass(TemporaryPasswordIssuedEvent.class);

		verify(temporaryPasswordRepository).save(passwordCaptor.capture());
		verify(eventPublisher).publishEvent(eventCaptor.capture());

		TemporaryPassword savedTemporaryPassword = passwordCaptor.getValue();
		TemporaryPasswordIssuedEvent event = eventCaptor.getValue();

		assertThat(savedTemporaryPassword.getUser()).isEqualTo(user);
		assertThat(savedTemporaryPassword.getPasswordHash()).isEqualTo(TEMPORARY_PASSWORD_HASH);
		assertThat(savedTemporaryPassword.getCreatedAt()).isNotNull();
		assertThat(savedTemporaryPassword.getExpiresAt()).isAfter(savedTemporaryPassword.getCreatedAt());

		assertThat(event.userId()).isEqualTo(user.getId());
		assertThat(event.email()).isEqualTo(EMAIL);
		assertThat(event.temporaryPassword()).isEqualTo(TEMPORARY_PASSWORD);
		assertThat(event.expiresAt()).isEqualTo(savedTemporaryPassword.getExpiresAt());
	}

	@Test
	@DisplayName("이미 임시 비밀번호가 있으면 새로 저장하지 않고 기존 임시 비밀번호를 갱신한다")
	void resetPassword_reissueTemporaryPassword_whenTemporaryPasswordExists() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL);
		Instant oldCreatedAt = Instant.now().minusSeconds(120);
		Instant oldExpiresAt = Instant.now().plusSeconds(60);

		TemporaryPassword savedTemporaryPassword = TemporaryPassword.builder()
			.user(user)
			.passwordHash("old-password-hash")
			.createdAt(oldCreatedAt)
			.expiresAt(oldExpiresAt)
			.build();

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(temporaryPasswordGenerator.generate()).thenReturn(TEMPORARY_PASSWORD);
		when(passwordEncoder.encode(TEMPORARY_PASSWORD)).thenReturn(TEMPORARY_PASSWORD_HASH);
		when(temporaryPasswordRepository.findByUser_Id(user.getId()))
			.thenReturn(Optional.of(savedTemporaryPassword));

		// when
		temporaryPasswordService.resetPassword(EMAIL);

		// then
		ArgumentCaptor<TemporaryPasswordIssuedEvent> eventCaptor =
			ArgumentCaptor.forClass(TemporaryPasswordIssuedEvent.class);

		assertThat(savedTemporaryPassword.getPasswordHash()).isEqualTo(TEMPORARY_PASSWORD_HASH);
		assertThat(savedTemporaryPassword.getCreatedAt()).isAfter(oldCreatedAt);
		assertThat(savedTemporaryPassword.getExpiresAt()).isAfter(savedTemporaryPassword.getCreatedAt());

		verify(temporaryPasswordRepository, never()).save(any(TemporaryPassword.class));
		verify(eventPublisher).publishEvent(eventCaptor.capture());

		TemporaryPasswordIssuedEvent event = eventCaptor.getValue();

		assertThat(event.userId()).isEqualTo(user.getId());
		assertThat(event.email()).isEqualTo(EMAIL);
		assertThat(event.temporaryPassword()).isEqualTo(TEMPORARY_PASSWORD);
		assertThat(event.expiresAt()).isEqualTo(savedTemporaryPassword.getExpiresAt());
	}

	@Test
	@DisplayName("존재하지 않는 이메일이면 UserException을 던지고 저장과 이벤트 발행을 하지 않는다")
	void resetPassword_throwUserException_whenUserNotFound() {
		// given
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> temporaryPasswordService.resetPassword(EMAIL))
			.isInstanceOf(UserException.class);

		verifyNoInteractions(temporaryPasswordGenerator);
		verifyNoInteractions(passwordEncoder);
		verifyNoInteractions(temporaryPasswordRepository);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("사용자 id로 임시 비밀번호를 삭제한다")
	void deleteTemporaryPassword_deleteByUserId() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		temporaryPasswordService.deleteTemporaryPassword(userId);

		// then
		verify(temporaryPasswordRepository).deleteByUser_Id(userId);
	}

	private User createUser(UUID userId, String email) {
		User user = User.builder()
			.name("사용자")
			.email(email)
			.passwordHash("encoded-password")
			.build();

		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}
}