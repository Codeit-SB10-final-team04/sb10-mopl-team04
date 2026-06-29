package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.auth.entity.AuthSession;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;

	@Mock
	private TokenHasher tokenHasher;

	@Mock
	private AuthSessionStore authSessionStore;

	@Mock
	private AuthSession authSession;

	@Mock
	private User user;

	@InjectMocks
	private AuthTokenService authTokenService;

	@Test
	@DisplayName("refresh token이 유효하면 새 access token과 refresh token을 재발급한다")
	void refresh_returnTokenRefreshResult_whenRefreshTokenIsValid() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		String refreshToken = "old-refresh-token";
		String currentRefreshTokenHash = "old-refresh-token-hash";
		String newRefreshToken = "new-refresh-token";
		String newRefreshTokenHash = "new-refresh-token-hash";
		String newAccessToken = "new-access-token";

		Instant accessExpiresAt = Instant.parse("2026-06-29T01:30:00Z");
		Instant refreshExpiresAt = Instant.parse("2026-07-13T01:00:00Z");

		given(tokenHasher.hash(refreshToken)).willReturn(currentRefreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(authSession));

		given(authSession.isRefreshTokenExpired(any(Instant.class))).willReturn(false);
		given(authSession.getUser()).willReturn(user);
		given(authSession.getSessionId()).willReturn(sessionId);

		givenUser(userId, false);

		given(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class)))
			.willReturn(accessExpiresAt);
		given(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class)))
			.willReturn(refreshExpiresAt);
		given(jwtTokenProvider.generateAccessToken(
			any(MoplUserDetails.class),
			eq(sessionId),
			any(Instant.class),
			eq(accessExpiresAt)
		)).willReturn(newAccessToken);

		given(refreshTokenGenerator.generate()).willReturn(newRefreshToken);
		given(tokenHasher.hash(newRefreshToken)).willReturn(newRefreshTokenHash);

		given(authSessionStore.refresh(
			eq(userId),
			eq(currentRefreshTokenHash),
			eq(newRefreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		)).willReturn(Optional.of(authSession));

		// when
		TokenRefreshResult result = authTokenService.refresh(refreshToken);

		// then
		assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
		assertThat(result.jwtDto().accessToken()).isEqualTo(newAccessToken);
		assertThat(result.jwtDto().userDto().id()).isEqualTo(userId);

		verify(jwtTokenProvider).generateAccessToken(
			any(MoplUserDetails.class),
			eq(sessionId),
			any(Instant.class),
			eq(accessExpiresAt)
		);
		verify(authSessionStore).refresh(
			eq(userId),
			eq(currentRefreshTokenHash),
			eq(newRefreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		);
	}

	@Test
	@DisplayName("refresh token이 없으면 토큰 재발급에 실패한다")
	void refresh_throwMissingRefreshToken_whenRefreshTokenIsNull() {
		// given

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(null))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MISSING_REFRESH_TOKEN)
			);

		verifyNoInteractions(tokenHasher);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("refresh token이 공백이면 토큰 재발급에 실패한다")
	void refresh_throwMissingRefreshToken_whenRefreshTokenIsBlank() {
		// given

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh("   "))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MISSING_REFRESH_TOKEN)
			);

		verifyNoInteractions(tokenHasher);
		verifyNoInteractions(authSessionStore);
	}

	@Test
	@DisplayName("refresh token hash에 해당하는 인증 세션이 없으면 토큰 재발급에 실패한다")
	void refresh_throwInvalidRefreshToken_whenAuthSessionDoesNotExist() {
		// given
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";

		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN)
			);

		verify(authSessionStore, never()).refresh(
			any(),
			any(),
			any(),
			any(),
			any(),
			any()
		);
	}

	@Test
	@DisplayName("refresh token이 만료되면 인증 세션을 삭제하고 토큰 재발급에 실패한다")
	void refresh_deleteAuthSessionAndThrowExpiredRefreshToken_whenRefreshTokenIsExpired() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";

		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(authSession));

		given(authSession.isRefreshTokenExpired(any(Instant.class))).willReturn(true);
		given(authSession.getUser()).willReturn(user);
		given(authSession.getSessionId()).willReturn(sessionId);
		given(user.getId()).willReturn(userId);

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN)
			);

		verify(authSessionStore).delete(userId, sessionId);
		verify(jwtTokenProvider, never()).generateAccessToken(
			any(),
			any(),
			any(),
			any()
		);
	}

	@Test
	@DisplayName("잠긴 계정이면 사용자 인증 세션을 삭제하고 토큰 재발급에 실패한다")
	void refresh_deleteAuthSessionAndThrowLockedAccount_whenUserIsLocked() {
		// given
		UUID userId = UUID.randomUUID();

		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";

		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(authSession));

		given(authSession.isRefreshTokenExpired(any(Instant.class))).willReturn(false);
		given(authSession.getUser()).willReturn(user);
		given(user.getId()).willReturn(userId);
		given(user.isLocked()).willReturn(true);

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOCKED_ACCOUNT)
			);

		verify(authSessionStore).deleteByUserId(userId);
		verify(jwtTokenProvider, never()).generateAccessToken(
			any(),
			any(),
			any(),
			any()
		);
	}

	@Test
	@DisplayName("인증 세션 갱신에 실패하면 토큰 재발급에 실패한다")
	void refresh_throwInvalidRefreshToken_whenAuthSessionRefreshFails() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		String refreshToken = "old-refresh-token";
		String currentRefreshTokenHash = "old-refresh-token-hash";
		String newRefreshToken = "new-refresh-token";
		String newRefreshTokenHash = "new-refresh-token-hash";
		String newAccessToken = "new-access-token";

		Instant accessExpiresAt = Instant.parse("2026-06-29T01:30:00Z");
		Instant refreshExpiresAt = Instant.parse("2026-07-13T01:00:00Z");

		given(tokenHasher.hash(refreshToken)).willReturn(currentRefreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(authSession));

		given(authSession.isRefreshTokenExpired(any(Instant.class))).willReturn(false);
		given(authSession.getUser()).willReturn(user);
		given(authSession.getSessionId()).willReturn(sessionId);

		givenUser(userId, false);

		given(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class)))
			.willReturn(accessExpiresAt);
		given(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class)))
			.willReturn(refreshExpiresAt);
		given(jwtTokenProvider.generateAccessToken(
			any(MoplUserDetails.class),
			eq(sessionId),
			any(Instant.class),
			eq(accessExpiresAt)
		)).willReturn(newAccessToken);

		given(refreshTokenGenerator.generate()).willReturn(newRefreshToken);
		given(tokenHasher.hash(newRefreshToken)).willReturn(newRefreshTokenHash);

		given(authSessionStore.refresh(
			eq(userId),
			eq(currentRefreshTokenHash),
			eq(newRefreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN)
			);
	}

	// 테스트용 사용자 mock 기본값 설정
	private void givenUser(UUID userId, boolean locked) {
		given(user.getId()).willReturn(userId);
		given(user.getCreatedAt()).willReturn(Instant.parse("2026-06-29T01:00:00Z"));
		given(user.getEmail()).willReturn("test@test.com");
		given(user.getName()).willReturn("사용자");
		given(user.getPasswordHashForAuthentication()).willReturn("encoded-password");
		given(user.getProfileImageUrl()).willReturn("https://example.com/profile.png");
		given(user.getRole()).willReturn(UserRole.USER);
		given(user.isLocked()).willReturn(locked);
	}
}