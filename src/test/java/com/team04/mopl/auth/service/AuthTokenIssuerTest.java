package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.service.dto.AuthTokenIssueResult;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthTokenIssuerTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;

	@Mock
	private TokenHasher tokenHasher;

	@Mock
	private AuthSessionStore authSessionStore;

	@Mock
	private MoplUserDetails userDetails;

	@Test
	@DisplayName("인증 사용자에게 access token과 refresh token을 발급하고 세션을 저장한다")
	void issue_returnTokensAndStoreSession_whenUserIsAuthenticated() {
		// given
		AuthTokenIssuer authTokenIssuer = new AuthTokenIssuer(
			jwtTokenProvider,
			refreshTokenGenerator,
			tokenHasher,
			authSessionStore
		);
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto(
			userId,
			Instant.now(),
			"user@gmail.com",
			"사용자",
			null,
			UserRole.USER,
			false
		);
		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		Instant refreshExpiresAt = Instant.now().plusSeconds(1209600);

		given(userDetails.getUserId()).willReturn(userId);
		given(userDetails.toUserDto()).willReturn(userDto);
		given(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class))).willReturn(accessExpiresAt);
		given(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class))).willReturn(refreshExpiresAt);
		given(jwtTokenProvider.generateAccessToken(
			any(MoplUserDetails.class),
			any(UUID.class),
			any(Instant.class),
			eq(accessExpiresAt)
		)).willReturn("access-token");
		given(refreshTokenGenerator.generate()).willReturn("refresh-token");
		given(tokenHasher.hash("refresh-token")).willReturn("refresh-token-hash");
		ArgumentCaptor<UUID> tokenSessionIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<UUID> storedSessionIdCaptor = ArgumentCaptor.forClass(UUID.class);

		// when
		AuthTokenIssueResult result = authTokenIssuer.issue(userDetails);

		// then
		assertThat(result.jwtDto()).isEqualTo(new JwtDto(userDto, "access-token"));
		assertThat(result.refreshToken()).isEqualTo("refresh-token");
		assertThat(result.toString()).doesNotContain("refresh-token");
		assertThat(result.toString()).contains("refreshToken=****");
		verify(jwtTokenProvider).generateAccessToken(
			eq(userDetails),
			tokenSessionIdCaptor.capture(),
			any(Instant.class),
			eq(accessExpiresAt)
		);
		verify(authSessionStore).replace(
			eq(userId),
			storedSessionIdCaptor.capture(),
			eq("refresh-token-hash"),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			any(Instant.class)
		);
		assertThat(storedSessionIdCaptor.getValue()).isEqualTo(tokenSessionIdCaptor.getValue());
	}
}
