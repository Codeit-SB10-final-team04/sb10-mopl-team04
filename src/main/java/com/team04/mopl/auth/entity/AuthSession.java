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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "auth_sessions",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_auth_sessions_user",
			columnNames = "user_id"
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession { // updatedAt만 활용하기 때문에 상속 안 함

	// JWT의 sid claim으로 사용할 인증 세션 식별자
	@Id
	@Column(name = "session_id", nullable = false, updatable = false, columnDefinition = "UUID")
	private UUID sessionId;

	// 인증 세션을 소유한 사용자 (사용자당 활성 세션은 하나)
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true, columnDefinition = "UUID")
	private User user;

	// refresh token 해시값
	@Column(name = "refresh_token_hash", nullable = false, columnDefinition = "TEXT")
	private String refreshTokenHash;

	// access token 만료 시각
	@Column(name = "access_expires_at", nullable = false)
	private Instant accessExpiresAt;

	// refresh token 만료 시각
	@Column(name = "refresh_expires_at", nullable = false)
	private Instant refreshExpiresAt;

	// 마지막 토큰 재발급 시각
	@Column(name = "last_refreshed_at")
	private Instant lastRefreshedAt;

	// 인증 세션 수정 시각
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Builder
	protected AuthSession(
		UUID sessionId,
		User user,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant updatedAt
	) {
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId는 필수입니다.");
		this.user = Objects.requireNonNull(user, "user는 필수입니다.");
		this.refreshTokenHash = requireText(refreshTokenHash);
		this.accessExpiresAt = Objects.requireNonNull(accessExpiresAt, "accessExpiresAt은 필수입니다.");
		this.refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt은 필수입니다.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt은 필수입니다.");

		validateExpiration(updatedAt, accessExpiresAt, refreshExpiresAt);
	}

	// Refresh Token 인증 세션 정보 갱신
	public void refresh(
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant refreshedAt
	) {
		Objects.requireNonNull(refreshedAt, "refreshedAt은 필수입니다.");

		validateExpiration(refreshedAt, accessExpiresAt, refreshExpiresAt);

		this.refreshTokenHash = requireText(refreshTokenHash);
		this.accessExpiresAt = accessExpiresAt;
		this.refreshExpiresAt = refreshExpiresAt;
		this.lastRefreshedAt = refreshedAt;
		this.updatedAt = refreshedAt;
	}

	public boolean matchesSessionId(UUID sessionId) {
		return this.sessionId.equals(sessionId);
	}

	public boolean matchesRefreshTokenHash(String refreshTokenHash) {
		return this.refreshTokenHash.equals(refreshTokenHash);
	}

	public boolean isAccessTokenExpired(Instant now) {
		Objects.requireNonNull(now, "현재시각은 필수입니다.");

		return !now.isBefore(accessExpiresAt);
	}

	public boolean isRefreshTokenExpired(Instant now) {
		Objects.requireNonNull(now, "현재시각은 필수입니다.");

		return !now.isBefore(refreshExpiresAt);
	}

	private static void validateExpiration(
		Instant issuedAt,
		Instant accessExpiresAt,
		Instant refreshExpiresAt
	) {
		Objects.requireNonNull(issuedAt, "issuedAt은 필수입니다.");
		Objects.requireNonNull(accessExpiresAt, "accessExpiresAt은 필수입니다.");
		Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt은 필수입니다.");

		if (!accessExpiresAt.isAfter(issuedAt)) {
			throw new IllegalArgumentException("Access Token 만료 시각은 발급 시각 이후여야 합니다.");
		}

		if (!refreshExpiresAt.isAfter(accessExpiresAt)) {
			throw new IllegalArgumentException("Refresh Token 만료 시각은 Access Token 만료 시각 이후여야 합니다.");
		}
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Refresh Token 해시는 필수입니다.");
		}

		return value;
	}
}