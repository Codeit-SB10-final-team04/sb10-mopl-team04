package com.team04.mopl.auth.security.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.auth.entity.TemporaryPassword;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MoplAuthenticationProviderTest {

	private static final String EMAIL = "user@example.com";
	private static final String RAW_PASSWORD = "password1234";
	private static final String PASSWORD_HASH = "encoded-password";
	private static final String TEMPORARY_PASSWORD_HASH = "encoded-temporary-password";

	@Mock
	private UserRepository userRepository;

	@Mock
	private TemporaryPasswordRepository temporaryPasswordRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private MoplAuthenticationProvider moplAuthenticationProvider;

	@Test
	@DisplayName("실제 비밀번호가 일치하면 인증에 성공한다")
	void authenticate_success_whenUserPasswordMatches() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, false);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

		// when
		Authentication result = moplAuthenticationProvider.authenticate(request);

		// then
		assertThat(result.isAuthenticated()).isTrue();
		assertThat(result.getPrincipal()).isInstanceOf(MoplUserDetails.class);

		verify(temporaryPasswordRepository, never()).findByUser_Id(user.getId());
	}

	@Test
	@DisplayName("실제 비밀번호가 틀려도 유효한 임시 비밀번호가 일치하면 인증에 성공한다")
	void authenticate_success_whenTemporaryPasswordMatches() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, false);
		TemporaryPassword temporaryPassword = createTemporaryPassword(
			user,
			Instant.now().minusSeconds(30),
			Instant.now().plusSeconds(120)
		);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
		when(temporaryPasswordRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(temporaryPassword));
		when(passwordEncoder.matches(RAW_PASSWORD, TEMPORARY_PASSWORD_HASH)).thenReturn(true);

		// when
		Authentication result = moplAuthenticationProvider.authenticate(request);

		// then
		assertThat(result.isAuthenticated()).isTrue();
		assertThat(result.getPrincipal()).isInstanceOf(MoplUserDetails.class);
	}

	@Test
	@DisplayName("사용자가 존재하지 않으면 BadCredentialsException을 던진다")
	void authenticate_throwBadCredentialsException_whenUserNotFound() {
		// given
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> moplAuthenticationProvider.authenticate(request))
			.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@DisplayName("잠긴 계정이면 LockedException을 던진다")
	void authenticate_throwLockedException_whenUserLocked() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, true);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

		// when, then
		assertThatThrownBy(() -> moplAuthenticationProvider.authenticate(request))
			.isInstanceOf(LockedException.class);

		verify(passwordEncoder, never()).matches(RAW_PASSWORD, PASSWORD_HASH);
		verify(temporaryPasswordRepository, never()).findByUser_Id(user.getId());
	}

	@Test
	@DisplayName("실제 비밀번호가 틀리고 임시 비밀번호가 없으면 BadCredentialsException을 던진다")
	void authenticate_throwBadCredentialsException_whenPasswordMismatchAndTemporaryPasswordNotExists() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, false);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
		when(temporaryPasswordRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> moplAuthenticationProvider.authenticate(request))
			.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@DisplayName("임시 비밀번호가 있어도 입력 비밀번호와 일치하지 않으면 BadCredentialsException을 던진다")
	void authenticate_throwBadCredentialsException_whenTemporaryPasswordMismatch() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, false);
		TemporaryPassword temporaryPassword = createTemporaryPassword(
			user,
			Instant.now().minusSeconds(30),
			Instant.now().plusSeconds(120)
		);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
		when(temporaryPasswordRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(temporaryPassword));
		when(passwordEncoder.matches(RAW_PASSWORD, TEMPORARY_PASSWORD_HASH)).thenReturn(false);

		// when, then
		assertThatThrownBy(() -> moplAuthenticationProvider.authenticate(request))
			.isInstanceOf(BadCredentialsException.class);

		verify(temporaryPasswordRepository, never()).deleteByUser_Id(user.getId());
	}

	@Test
	@DisplayName("임시 비밀번호가 만료되었으면 삭제하고 BadCredentialsException을 던진다")
	void authenticate_deleteTemporaryPasswordAndThrowBadCredentialsException_whenTemporaryPasswordExpired() {
		// given
		User user = createUser(UUID.randomUUID(), EMAIL, false);
		TemporaryPassword temporaryPassword = createTemporaryPassword(
			user,
			Instant.now().minusSeconds(300),
			Instant.now().minusSeconds(1)
		);
		Authentication request = new UsernamePasswordAuthenticationToken(EMAIL, RAW_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
		when(temporaryPasswordRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(temporaryPassword));

		// when, then
		assertThatThrownBy(() -> moplAuthenticationProvider.authenticate(request))
			.isInstanceOf(BadCredentialsException.class);

		verify(temporaryPasswordRepository).deleteByUser_Id(user.getId());
	}

	@Test
	@DisplayName("UsernamePasswordAuthenticationToken 타입을 지원한다")
	void supports_returnTrue_whenUsernamePasswordAuthenticationToken() {
		// when
		boolean result = moplAuthenticationProvider.supports(UsernamePasswordAuthenticationToken.class);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("UsernamePasswordAuthenticationToken 타입이 아니면 지원하지 않는다")
	void supports_returnFalse_whenAuthenticationTypeIsNotUsernamePasswordAuthenticationToken() {
		// when
		boolean result = moplAuthenticationProvider.supports(Authentication.class);

		// then
		assertThat(result).isFalse();
	}

	private User createUser(UUID userId, String email, boolean locked) {
		User user = User.builder()
			.name("사용자")
			.email(email)
			.passwordHash(PASSWORD_HASH)
			.locked(locked)
			.build();

		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}

	private TemporaryPassword createTemporaryPassword(User user, Instant createdAt, Instant expiresAt) {
		return TemporaryPassword.builder()
			.user(user)
			.passwordHash(TEMPORARY_PASSWORD_HASH)
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build();
	}
}