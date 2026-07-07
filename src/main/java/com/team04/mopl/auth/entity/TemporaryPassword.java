package com.team04.mopl.auth.entity;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "temporary_passwords")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemporaryPassword {

	// users.id를 그대로 PK로 사용하는 공유 기본키
	@Id
	@Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "UUID")
	private UUID userId;

	// 임시 비밀번호가 발급된 사용자
	// @MapsId를 통해 User의 id를 TemporaryPassword의 PK로 사용
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, columnDefinition = "UUID")
	private User user;

	// 임시 비밀번호 원문이 아닌 해시값
	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	// 임시 비밀번호 만료 시각
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	// 임시 비밀번호 발급 시각
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Builder
	protected TemporaryPassword(
		User user,
		String passwordHash,
		Instant expiresAt,
		Instant createdAt
	) {
		validateUser(user);
		validatePasswordHash(passwordHash);
		validateExpiresAt(expiresAt);
		validateCreatedAt(createdAt);
		validateExpiration(createdAt, expiresAt);

		this.user = user;
		this.passwordHash = passwordHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	// 임시 비밀번호 재발급
	public void reissue(String passwordHash, Instant createdAt, Instant expiresAt) {
		validatePasswordHash(passwordHash);
		validateCreatedAt(createdAt);
		validateExpiresAt(expiresAt);
		validateExpiration(createdAt, expiresAt);

		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	// 임시 비밀번호가 만료되었는지 확인
	public boolean isExpired(Instant now) {
		validateCurrentTime(now);

		return !now.isBefore(expiresAt);
	}

	private static void validateUser(User user) {
		if (user == null) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE);
		}
	}

	private static void validatePasswordHash(String passwordHash) {
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE);
		}
	}

	private static void validateExpiresAt(Instant expiresAt) {
		if (expiresAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE);
		}
	}

	private static void validateCreatedAt(Instant createdAt) {
		if (createdAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE);
		}
	}

	private static void validateCurrentTime(Instant now) {
		if (now == null) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE);
		}
	}

	private static void validateExpiration(Instant createdAt, Instant expiresAt) {
		if (!expiresAt.isAfter(createdAt)) {
			throw new AuthException(AuthErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRATION_INVALID);
		}
	}
}
