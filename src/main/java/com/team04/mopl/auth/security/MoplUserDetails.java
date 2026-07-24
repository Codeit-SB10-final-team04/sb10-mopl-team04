package com.team04.mopl.auth.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;

/**
 * Spring Security의 Principal로 사용할 사용자 인증 정보 클래스
 *
 * - 로그인 성공 시에는 DB에서 조회한 User를 기반으로 생성
 * - JWT 인증 필터에서는 access token claim을 기반으로 최소 정보만 복원
 */
public class MoplUserDetails implements UserDetails {

	private static final String ROLE_PREFIX = "ROLE_";

	private final UUID userId;
	private final UUID sessionId;
	private final Instant createdAt;
	private final String email;
	private final String name;
	private final String profileImageUrl;
	private final UserRole role;
	private final boolean locked;
	private final String passwordHash;

	private MoplUserDetails(
		UUID userId,
		UUID sessionId,
		Instant createdAt,
		String email,
		String name,
		String profileImageUrl,
		UserRole role,
		boolean locked,
		String passwordHash
	) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.createdAt = createdAt;
		this.email = email;
		this.name = name;
		this.profileImageUrl = profileImageUrl;
		this.role = role;
		this.locked = locked;
		this.passwordHash = passwordHash;
	}

	// DB User 엔티티를 기반으로 로그인용 Principal 생성
	public static MoplUserDetails from(User user) {
		return new MoplUserDetails(
			user.getId(),
			null,
			user.getCreatedAt(),
			user.getEmail(),
			user.getName(),
			user.getProfileImageUrl(),
			user.getRole(),
			user.isLocked(),
			user.getPasswordHashForAuthentication()
		);
	}

	// JWT 인증 필터에서 token claim 정보를 기반으로 인증 완료 Principal 생성
	public static MoplUserDetails authenticated(
		UUID userId,
		String email,
		UserRole role
	) {
		return authenticated(userId, null, email, role);
	}

	// 실시간 연결의 인증 세션 추적용 Principal 생성
	public static MoplUserDetails authenticated(
		UUID userId,
		UUID sessionId,
		String email,
		UserRole role
	) {
		return new MoplUserDetails(
			userId,
			sessionId,
			null,
			email,
			null,
			null,
			role,
			false,
			null
		);
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public String getEmail() {
		return email;
	}

	public UserRole getRole() {
		return role;
	}

	// 로그인 성공 응답 JwtDto 안에 넣을 UserDto 생성
	public UserDto toUserDto() {
		return new UserDto(
			userId,
			createdAt,
			email,
			name,
			profileImageUrl,
			role,
			locked
		);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !locked;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
