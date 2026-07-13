package com.team04.mopl.auth.security.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.support.AuthResponseWriter;
import com.team04.mopl.auth.service.AuthTokenIssuer;
import com.team04.mopl.auth.service.dto.AuthTokenIssueResult;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;

class LoginSuccessHandlerTest {

	private final AuthTokenIssuer authTokenIssuer = mock(AuthTokenIssuer.class);
	private final RefreshTokenCookieWriter refreshTokenCookieWriter = mock(RefreshTokenCookieWriter.class);
	private final AuthResponseWriter authResponseWriter = mock(AuthResponseWriter.class);

	private final LoginSuccessHandler loginSuccessHandler = new LoginSuccessHandler(
		authTokenIssuer,
		refreshTokenCookieWriter,
		authResponseWriter
	);

	@Test
	@DisplayName("로그인 성공 시 공통 토큰 발급 결과를 응답한다")
	void onAuthenticationSuccess_writesJwtResponse_whenAuthenticationSucceeds() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		String accessToken = "access-token";
		String refreshToken = "refresh-token";
		UserDto userDto = new UserDto(
			userId,
			Instant.now(),
			"test@test.com",
			"테스트 사용자",
			null,
			UserRole.USER,
			false
		);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		Authentication authentication = mock(Authentication.class);
		MoplUserDetails userDetails = mock(MoplUserDetails.class);

		when(authentication.getPrincipal()).thenReturn(userDetails);
		when(authTokenIssuer.issue(userDetails))
			.thenReturn(new AuthTokenIssueResult(new JwtDto(userDto, accessToken), refreshToken));

		// when
		loginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

		// then
		verify(authTokenIssuer).issue(userDetails);
		verify(refreshTokenCookieWriter).write(response, refreshToken);
		verify(authResponseWriter).writeJson(
			response,
			HttpStatus.OK,
			new JwtDto(userDto, accessToken)
		);
	}
}
