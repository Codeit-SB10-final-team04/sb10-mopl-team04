package com.team04.mopl.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
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
		validateSessionId(sessionId);
		validateUser(user);
		validateRefreshTokenHash(refreshTokenHash);
		validateAccessExpiresAt(accessExpiresAt);
		validateRefreshExpiresAt(refreshExpiresAt);
		validateUpdatedAt(updatedAt);
		validateExpiration(updatedAt, accessExpiresAt, refreshExpiresAt);

		this.sessionId = sessionId;
		this.user = user;
		this.refreshTokenHash = refreshTokenHash;
		this.accessExpiresAt = accessExpiresAt;
		this.refreshExpiresAt = refreshExpiresAt;
		this.updatedAt = updatedAt;
	}

	// Refresh Token 인증 세션 정보 갱신
	public void refresh(
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant refreshedAt
	) {
		validateRefreshTokenHash(refreshTokenHash);
		validateAccessExpiresAt(accessExpiresAt);
		validateRefreshExpiresAt(refreshExpiresAt);
		validateRefreshedAt(refreshedAt);
		validateExpiration(refreshedAt, accessExpiresAt, refreshExpiresAt);

		this.refreshTokenHash = refreshTokenHash;
		this.accessExpiresAt = accessExpiresAt;
		this.refreshExpiresAt = refreshExpiresAt;
		this.lastRefreshedAt = refreshedAt;
		this.updatedAt = refreshedAt;
	}

	// sessionId 일치 여부 확인
	public boolean matchesSessionId(UUID sessionId) {
		return this.sessionId.equals(sessionId);
	}

	// refresh token hash 일치 여부 확인
	public boolean matchesRefreshTokenHash(String refreshTokenHash) {
		return this.refreshTokenHash.equals(refreshTokenHash);
	}

	// access token 만료 여부 확인
	public boolean isAccessTokenExpired(Instant now) {
		validateCurrentTime(now);

		return !now.isBefore(accessExpiresAt);
	}

	// refresh token 만료 여부 확인
	public boolean isRefreshTokenExpired(Instant now) {
		validateCurrentTime(now);

		return !now.isBefore(refreshExpiresAt);
	}

	private static void validateSessionId(UUID sessionId) {
		if (sessionId == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateUser(User user) {
		if (user == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateRefreshTokenHash(String refreshTokenHash) {
		if (refreshTokenHash == null || refreshTokenHash.isBlank()) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateAccessExpiresAt(Instant accessExpiresAt) {
		if (accessExpiresAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateRefreshExpiresAt(Instant refreshExpiresAt) {
		if (refreshExpiresAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateUpdatedAt(Instant updatedAt) {
		if (updatedAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateRefreshedAt(Instant refreshedAt) {
		if (refreshedAt == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateCurrentTime(Instant now) {
		if (now == null) {
			throw new AuthException(AuthErrorCode.AUTH_SESSION_REQUIRED_VALUE);
		}
	}

	private static void validateExpiration(
		Instant issuedAt,
		Instant accessExpiresAt,
		Instant refreshExpiresAt
	) {
		validateUpdatedAt(issuedAt);
		validateAccessExpiresAt(accessExpiresAt);
		validateRefreshExpiresAt(refreshExpiresAt);

		if (!accessExpiresAt.isAfter(issuedAt)) {
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_EXPIRATION_INVALID);
		}

		if (!refreshExpiresAt.isAfter(accessExpiresAt)) {
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_EXPIRATION_INVALID);
		}
	}
}