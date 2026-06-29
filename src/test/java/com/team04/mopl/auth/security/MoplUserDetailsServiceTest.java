package com.team04.mopl.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

class MoplUserDetailsServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final MoplUserDetailsService userDetailsService = new MoplUserDetailsService(userRepository);

	@Test
	@DisplayName("이메일로 사용자를 조회해 UserDetails를 반환한다")
	void loadUserByUsername_returnsUserDetails_whenUserExists() {
		// given
		String email = "test@test.com";
		User user = mock(User.class);

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(user.isPasswordLoginSupported()).thenReturn(true);
		when(user.getId()).thenReturn(UUID.randomUUID());
		when(user.getCreatedAt()).thenReturn(Instant.now());
		when(user.getEmail()).thenReturn(email);
		when(user.getName()).thenReturn("테스트유저");
		when(user.getProfileImageUrl()).thenReturn(null);
		when(user.getRole()).thenReturn(UserRole.USER);
		when(user.isLocked()).thenReturn(false);
		when(user.getPasswordHashForAuthentication()).thenReturn("encoded-password");

		// when
		MoplUserDetails userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);

		// then
		assertThat(userDetails.getUsername()).isEqualTo(email);
		assertThat(userDetails.getEmail()).isEqualTo(email);
		assertThat(userDetails.getRole()).isEqualTo(UserRole.USER);
		assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
	}

	@Test
	@DisplayName("사용자가 없으면 UsernameNotFoundException이 발생한다")
	void loadUserByUsername_throwsException_whenUserNotFound() {
		// given
		String email = "notfound@test.com";

		when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
			.isInstanceOf(UsernameNotFoundException.class)
			.hasMessage("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("비밀번호 로그인을 지원하지 않는 사용자이면 UsernameNotFoundException이 발생한다")
	void loadUserByUsername_throwsException_whenPasswordLoginUnsupported() {
		// given
		String email = "social@test.com";
		User user = mock(User.class);

		when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
		when(user.isPasswordLoginSupported()).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
			.isInstanceOf(UsernameNotFoundException.class)
			.hasMessage("사용자를 찾을 수 없습니다.");
	}
}