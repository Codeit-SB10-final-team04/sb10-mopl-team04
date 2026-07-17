package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.auth.session.AuthSessionData;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.mapper.UserMapper;
import com.team04.mopl.user.repository.UserRepository;

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
	private UserRepository userRepository;

	@Mock
	private UserMapper userMapper;

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
		Instant createdAt = Instant.now().minusSeconds(3600);
		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		Instant refreshExpiresAt = Instant.now().plusSeconds(1209600);
		AuthSessionData authSession = activeSession(userId, sessionId, currentRefreshTokenHash);
		UserDto userDto = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"사용자",
			"https://example.com/profile.png",
			UserRole.USER,
			false
		);

		given(tokenHasher.hash(refreshToken)).willReturn(currentRefreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(authSession));
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		givenUser(userId, createdAt, false);
		given(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class))).willReturn(accessExpiresAt);
		given(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class))).willReturn(refreshExpiresAt);
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
			eq(sessionId),
			eq(currentRefreshTokenHash),
			eq(newRefreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		)).willReturn(true);
		given(userMapper.toDto(user)).willReturn(userDto);

		// when
		TokenRefreshResult result = authTokenService.refresh(refreshToken);

		// then
		assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
		assertThat(result.jwtDto()).isEqualTo(new JwtDto(userDto, newAccessToken));
		verify(authSessionStore).refresh(
			eq(userId),
			eq(sessionId),
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
		String refreshToken = null;

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN)
			);

		verifyNoInteractions(
			tokenHasher,
			authSessionStore,
			userRepository,
			jwtTokenProvider,
			refreshTokenGenerator,
			userMapper
		);
	}

	@Test
	@DisplayName("refresh token이 공백이면 토큰 재발급에 실패한다")
	void refresh_throwMissingRefreshToken_whenRefreshTokenIsBlank() {
		// given
		String refreshToken = " ";

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN)
			);

		verifyNoInteractions(
			tokenHasher,
			authSessionStore,
			userRepository,
			jwtTokenProvider,
			refreshTokenGenerator,
			userMapper
		);
	}

	@Test
	@DisplayName("refresh token hash에 해당하는 인증 세션이 없으면 토큰 재발급에 실패한다")
	void refresh_throwInvalidRefreshToken_whenAuthSessionDoesNotExist() {
		// given
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";
		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN)
			);

		verifyNoInteractions(userRepository, jwtTokenProvider, refreshTokenGenerator, userMapper);
	}

	@Test
	@DisplayName("refresh token이 만료되면 인증 세션을 삭제하고 토큰 재발급에 실패한다")
	void refresh_deleteAuthSessionAndThrowExpiredRefreshToken_whenRefreshTokenIsExpired() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";
		AuthSessionData authSession = expiredSession(userId, sessionId, refreshTokenHash);
		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(authSession));

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_EXPIRED_REFRESH_TOKEN)
			);

		verify(authSessionStore).delete(userId, sessionId);
		verifyNoInteractions(userRepository, jwtTokenProvider, refreshTokenGenerator, userMapper);
	}

	@Test
	@DisplayName("인증 세션 사용자 정보가 없으면 세션을 삭제하고 토큰 재발급에 실패한다")
	void refresh_deleteAuthSessionAndThrowInvalidRefreshToken_whenUserDoesNotExist() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";
		AuthSessionData authSession = activeSession(userId, sessionId, refreshTokenHash);
		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(authSession));
		given(userRepository.findById(userId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN)
			);

		verify(authSessionStore).delete(userId, sessionId);
		verifyNoInteractions(jwtTokenProvider, refreshTokenGenerator, userMapper);
	}

	@Test
	@DisplayName("잠긴 계정이면 사용자 인증 세션을 삭제하고 토큰 재발급에 실패한다")
	void refresh_deleteAuthSessionAndThrowLockedAccount_whenUserIsLocked() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";
		AuthSessionData authSession = activeSession(userId, sessionId, refreshTokenHash);
		given(tokenHasher.hash(refreshToken)).willReturn(refreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(authSession));
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(user.getId()).willReturn(userId);
		given(user.isLocked()).willReturn(true);

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_LOCKED_ACCOUNT)
			);

		verify(authSessionStore).deleteByUserId(userId);
		verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
		verifyNoInteractions(refreshTokenGenerator, userMapper);
	}

	@Test
	@DisplayName("인증 세션 원자 갱신에 실패하면 토큰 재발급에 실패한다")
	void refresh_throwInvalidRefreshToken_whenAuthSessionRefreshFails() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshToken = "old-refresh-token";
		String currentRefreshTokenHash = "old-refresh-token-hash";
		String newRefreshToken = "new-refresh-token";
		String newRefreshTokenHash = "new-refresh-token-hash";
		Instant createdAt = Instant.now().minusSeconds(3600);
		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		Instant refreshExpiresAt = Instant.now().plusSeconds(1209600);
		AuthSessionData authSession = activeSession(userId, sessionId, currentRefreshTokenHash);
		given(tokenHasher.hash(refreshToken)).willReturn(currentRefreshTokenHash);
		given(authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(authSession));
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		givenUser(userId, createdAt, false);
		given(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class))).willReturn(accessExpiresAt);
		given(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class))).willReturn(refreshExpiresAt);
		given(jwtTokenProvider.generateAccessToken(
			any(MoplUserDetails.class),
			eq(sessionId),
			any(Instant.class),
			eq(accessExpiresAt)
		)).willReturn("new-access-token");
		given(refreshTokenGenerator.generate()).willReturn(newRefreshToken);
		given(tokenHasher.hash(newRefreshToken)).willReturn(newRefreshTokenHash);
		given(authSessionStore.refresh(
			eq(userId),
			eq(sessionId),
			eq(currentRefreshTokenHash),
			eq(newRefreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		)).willReturn(false);

		// when & then
		assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN)
			);

		verify(userMapper, never()).toDto(any());
	}

	private AuthSessionData activeSession(UUID userId, UUID sessionId, String refreshTokenHash) {
		Instant updatedAt = Instant.now();

		return new AuthSessionData(
			userId,
			sessionId,
			refreshTokenHash,
			updatedAt.plusSeconds(1800),
			updatedAt.plusSeconds(1209600),
			null,
			updatedAt
		);
	}

	private AuthSessionData expiredSession(UUID userId, UUID sessionId, String refreshTokenHash) {
		Instant updatedAt = Instant.now().minusSeconds(10800);

		return new AuthSessionData(
			userId,
			sessionId,
			refreshTokenHash,
			updatedAt.plusSeconds(1800),
			updatedAt.plusSeconds(7200),
			null,
			updatedAt
		);
	}

	private void givenUser(UUID userId, Instant createdAt, boolean locked) {
		given(user.getId()).willReturn(userId);
		given(user.getCreatedAt()).willReturn(createdAt);
		given(user.getEmail()).willReturn("test@test.com");
		given(user.getName()).willReturn("사용자");
		given(user.getPasswordHashForAuthentication()).willReturn("encoded-password");
		given(user.getProfileImageUrl()).willReturn("https://example.com/profile.png");
		given(user.getRole()).willReturn(UserRole.USER);
		given(user.isLocked()).willReturn(locked);
	}
}
