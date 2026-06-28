package com.team04.mopl.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;

class MoplUserDetailsTest {

	@Test
	@DisplayName("User 엔티티로 로그인용 Principal을 생성한다")
	void from_returnsUserDetails_whenUserEntityProvided() {
		// given
		UUID userId = UUID.randomUUID();
		User user = mock(User.class);

		when(user.getId()).thenReturn(userId);
		when(user.getCreatedAt()).thenReturn(Instant.now());
		when(user.getEmail()).thenReturn("test@test.com");
		when(user.getName()).thenReturn("테스트유저");
		when(user.getProfileImageUrl()).thenReturn(null);
		when(user.getRole()).thenReturn(UserRole.USER);
		when(user.isLocked()).thenReturn(false);
		when(user.getPasswordHashForAuthentication()).thenReturn("encoded-password");

		// when
		MoplUserDetails userDetails = MoplUserDetails.from(user);

		// then
		assertThat(userDetails.getUserId()).isEqualTo(userId);
		assertThat(userDetails.getUsername()).isEqualTo("test@test.com");
		assertThat(userDetails.getEmail()).isEqualTo("test@test.com");
		assertThat(userDetails.getRole()).isEqualTo(UserRole.USER);
		assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
		assertThat(userDetails.isAccountNonLocked()).isTrue();
	}

	@Test
	@DisplayName("JWT Claim으로 인증 완료 Principal을 생성한다")
	void authenticated_returnsUserDetails_whenJwtClaimsProvided() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			userId,
			"test@test.com",
			UserRole.ADMIN
		);

		// then
		assertThat(userDetails.getUserId()).isEqualTo(userId);
		assertThat(userDetails.getUsername()).isEqualTo("test@test.com");
		assertThat(userDetails.getEmail()).isEqualTo("test@test.com");
		assertThat(userDetails.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(userDetails.getPassword()).isNull();
		assertThat(userDetails.isAccountNonLocked()).isTrue();
	}

	@Test
	@DisplayName("사용자 권한을 ROLE_ prefix가 붙은 권한으로 반환한다")
	void getAuthorities_returnsRoleAuthority_whenRoleExists() {
		// given
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			UUID.randomUUID(),
			"admin@test.com",
			UserRole.ADMIN
		);

		// when & then
		assertThat(userDetails.getAuthorities())
			.extracting("authority")
			.containsExactly("ROLE_ADMIN");
	}

	@Test
	@DisplayName("잠긴 사용자이면 계정 잠금 상태를 false로 반환한다")
	void isAccountNonLocked_returnsFalse_whenUserIsLocked() {
		// given
		User user = mock(User.class);

		when(user.getId()).thenReturn(UUID.randomUUID());
		when(user.getCreatedAt()).thenReturn(Instant.now());
		when(user.getEmail()).thenReturn("locked@test.com");
		when(user.getName()).thenReturn("잠긴유저");
		when(user.getProfileImageUrl()).thenReturn(null);
		when(user.getRole()).thenReturn(UserRole.USER);
		when(user.isLocked()).thenReturn(true);
		when(user.getPasswordHashForAuthentication()).thenReturn("encoded-password");

		MoplUserDetails userDetails = MoplUserDetails.from(user);

		// when & then
		assertThat(userDetails.isAccountNonLocked()).isFalse();
	}

	@Test
	@DisplayName("계정 만료, 인증 정보 만료, 활성화 상태는 기본적으로 true를 반환한다")
	void accountStatus_returnsTrue_whenDefaultStatusChecked() {
		// given
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			UUID.randomUUID(),
			"test@test.com",
			UserRole.USER
		);

		// when & then
		assertThat(userDetails.isAccountNonExpired()).isTrue();
		assertThat(userDetails.isCredentialsNonExpired()).isTrue();
		assertThat(userDetails.isEnabled()).isTrue();
	}
}