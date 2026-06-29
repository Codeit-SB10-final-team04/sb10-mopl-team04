package com.team04.mopl.user.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.team04.mopl.common.entity.BaseUpdatableEntity;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {

	// 사용자 이름
	@Column(nullable = false, length = 50)
	private String name;

	// 로그인 및 사용자 식별에 사용되는 이메일
	@Column(nullable = false, unique = true, length = 255)
	private String email;

	// 실제 이메일 또는 소셜 가입용 가상 이메일 구분 (카카오는 가상 이메일 활용해야 함)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "email_type", nullable = false, columnDefinition = "email_type")
	private EmailType emailType;

	// 일반 로그인용 비밀번호 해시
	// 소셜 가입 사용자는 비밀번호가 없으므로 null 가능
	@Getter(AccessLevel.NONE)
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	// 프로필 이미지
	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	// 사용자 권한
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private UserRole role;

	// 계정 잠금 상태
	@Column(name = "is_locked", nullable = false)
	private boolean locked;

	@Builder
	protected User(
		String name,
		String email,
		EmailType emailType,
		String passwordHash,
		String profileImageUrl,
		UserRole role,
		Boolean locked
	) {
		validateName(name);
		validateEmail(email);

		this.name = name;
		this.email = email;
		this.emailType = emailType == null ? EmailType.REAL : emailType;
		this.passwordHash = passwordHash;
		this.profileImageUrl = profileImageUrl;
		this.role = role == null ? UserRole.USER : role;
		this.locked = Boolean.TRUE.equals(locked);
	}

	// 프로필 이름 변경
	public void updateName(String name) {
		validateName(name);
		this.name = name;
	}

	// 프로필 이미지 변경
	public void updateProfileImageUrl(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	// 비밀번호 변경
	public void updatePasswordHash(String passwordHash) {
		validatePasswordHash(passwordHash);
		this.passwordHash = passwordHash;
	}

	// 사용자 권한 변경
	public void updateRole(UserRole role) {
		validateRole(role);
		this.role = role;
	}

	// 계정 잠금 상태 변경
	public void updateLocked(boolean locked) {
		this.locked = locked;
	}

	// 일반 비밀번호 로그인을 지원하는 계정인지 확인
	public boolean isPasswordLoginSupported() {
		return passwordHash != null && !passwordHash.isBlank();
	}

	// 관리자 권한 여부 확인
	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	// 비밀번호 인증용
	@JsonIgnore
	public String getPasswordHashForAuthentication() {
		return passwordHash;
	}

	private static void validateName(String name) {
		if (name == null || name.isBlank()) {
			throw new UserException(UserErrorCode.NAME_REQUIRED);
		}
	}

	private static void validateEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new UserException(UserErrorCode.EMAIL_REQUIRED);
		}
	}

	private static void validatePasswordHash(String passwordHash) {
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new UserException(UserErrorCode.PASSWORD_REQUIRED);
		}
	}

	private static void validateRole(UserRole role) {
		if (role == null) {
			throw new UserException(UserErrorCode.ROLE_REQUIRED);
		}
	}
}
