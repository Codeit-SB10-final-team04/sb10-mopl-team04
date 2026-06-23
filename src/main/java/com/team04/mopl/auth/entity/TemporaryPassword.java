package com.team04.mopl.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
	@JoinColumn(
		name = "user_id",
		nullable = false,
		columnDefinition = "UUID"
	)
	private User user;

	// 임시 비밀번호 원문이 아닌 해시값
	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	// 임시 비밀번호 만료 시각
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	// 임시 비밀번호 발급 시각
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	protected TemporaryPassword(
		User user,
		String passwordHash,
		Instant expiresAt,
		Instant createdAt
	) {
		this.user = Objects.requireNonNull(user, "user는 필수입니다.");
		this.passwordHash = requireText(passwordHash);
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수입니다.");

		validateExpiration(createdAt, expiresAt);
	}

	// 기존 임시 비밀번호를 새로운 임시 비밀번호로 교체
	public void reissue(
		String passwordHash,
		Instant expiresAt,
		Instant issuedAt
	) {
		Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
		Objects.requireNonNull(issuedAt, "issuedAt은 필수입니다.");

		validateExpiration(issuedAt, expiresAt);

		this.passwordHash = requireText(passwordHash);
		this.expiresAt = expiresAt;
		this.createdAt = issuedAt;
	}

	// 임시 비밀번호가 만료되었는지 확인
	public boolean isExpired(Instant now) {
		Objects.requireNonNull(now, "now는 필수입니다.");

		return !now.isBefore(expiresAt);
	}

	private static void validateExpiration(
		Instant issuedAt,
		Instant expiresAt
	) {
		if (!expiresAt.isAfter(issuedAt)) {
			throw new IllegalArgumentException("임시 비밀번호 만료 시각은 발급 시각 이후여야 합니다.");
		}
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("임시 비밀번호 해시는 필수입니다.");
		}

		return value;
	}
}
