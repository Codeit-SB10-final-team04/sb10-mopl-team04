package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;

class LoginSuccessHandlerTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final RefreshTokenGenerator refreshTokenGenerator = mock(RefreshTokenGenerator.class);
	private final TokenHasher tokenHasher = mock(TokenHasher.class);
	private final RefreshTokenCookieWriter refreshTokenCookieWriter = mock(RefreshTokenCookieWriter.class);
	private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);

	private final LoginSuccessHandler loginSuccessHandler = new LoginSuccessHandler(
		jwtTokenProvider,
		refreshTokenGenerator,
		tokenHasher,
		refreshTokenCookieWriter,
		authSessionStore,
		authResponseWriter
	);

	@Test
	@DisplayName("로그인 성공 시 토큰을 발급하고 동일한 세션 정보로 인증 세션을 저장한다")
	void onAuthenticationSuccess_writesJwtResponse_whenAuthenticationSucceeds() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		String email = "test@test.com";
		String accessToken = "access-token";
		String refreshToken = "refresh-token";
		String refreshTokenHash = "refresh-token-hash";

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		Instant refreshExpiresAt = Instant.now().plusSeconds(1209600);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		Authentication authentication = mock(Authentication.class);
		MoplUserDetails userDetails = mock(MoplUserDetails.class);

		UserDto userDto = new UserDto(
			userId,
			Instant.now(),
			email,
			"테스트유저",
			null,
			UserRole.USER,
			false
		);

		when(authentication.getPrincipal()).thenReturn(userDetails);
		when(userDetails.getUserId()).thenReturn(userId);
		when(userDetails.toUserDto()).thenReturn(userDto);

		when(jwtTokenProvider.calculateAccessExpiresAt(any(Instant.class))).thenReturn(accessExpiresAt);
		when(jwtTokenProvider.calculateRefreshExpiresAt(any(Instant.class))).thenReturn(refreshExpiresAt);
		when(jwtTokenProvider.generateAccessToken(
			eq(userDetails),
			any(UUID.class),
			any(Instant.class),
			eq(accessExpiresAt)
		)).thenReturn(accessToken);

		when(refreshTokenGenerator.generate()).thenReturn(refreshToken);
		when(tokenHasher.hash(refreshToken)).thenReturn(refreshTokenHash);

		ArgumentCaptor<UUID> tokenSessionIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<Instant> tokenIssuedAtCaptor = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<UUID> storedSessionIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<Instant> storedIssuedAtCaptor = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<JwtDto> jwtDtoCaptor = ArgumentCaptor.forClass(JwtDto.class);

		// when
		loginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

		// then
		verify(jwtTokenProvider).generateAccessToken(
			eq(userDetails),
			tokenSessionIdCaptor.capture(),
			tokenIssuedAtCaptor.capture(),
			eq(accessExpiresAt)
		);

		verify(authSessionStore).replace(
			eq(userId),
			storedSessionIdCaptor.capture(),
			eq(refreshTokenHash),
			eq(accessExpiresAt),
			eq(refreshExpiresAt),
			storedIssuedAtCaptor.capture()
		);

		verify(refreshTokenCookieWriter).write(same(response), eq(refreshToken));
		verify(authResponseWriter).writeJson(same(response), eq(HttpStatus.OK), jwtDtoCaptor.capture());

		assertThat(storedSessionIdCaptor.getValue()).isEqualTo(tokenSessionIdCaptor.getValue());
		assertThat(storedIssuedAtCaptor.getValue()).isEqualTo(tokenIssuedAtCaptor.getValue());

		JwtDto jwtDto = jwtDtoCaptor.getValue();

		assertThat(jwtDto.userDto()).isEqualTo(userDto);
		assertThat(jwtDto.accessToken()).isEqualTo(accessToken);
	}
}