package com.team04.mopl.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.auth.security.jwt.JwtProperties;
import com.team04.mopl.auth.service.AuthTokenService;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.common.exception.GlobalExceptionHandler;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(
	controllers = AuthController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthTokenService authTokenService;

	@MockitoBean
	private RefreshTokenCookieWriter refreshTokenCookieWriter;

	@MockitoBean
	private JwtProperties jwtProperties;

	@Test
	@DisplayName("refresh token 쿠키가 있으면 200 OK와 JwtDto를 반환한다")
	void refresh_returnOkAndJwtDto_whenRefreshTokenCookieExists() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		String refreshToken = "old-refresh-token";
		String newRefreshToken = "new-refresh-token";
		String accessToken = "new-access-token";

		UserDto userDto = new UserDto(
			userId,
			Instant.parse("2026-06-29T01:00:00Z"),
			"test@test.com",
			"사용자",
			"https://example.com/profile.png",
			UserRole.USER,
			false
		);
		JwtDto jwtDto = new JwtDto(userDto, accessToken);

		given(jwtProperties.refreshTokenCookieName()).willReturn("REFRESH_TOKEN");
		given(authTokenService.refresh(refreshToken))
			.willReturn(new TokenRefreshResult(jwtDto, newRefreshToken));

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("REFRESH_TOKEN", refreshToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userDto.id").value(userId.toString()))
			.andExpect(jsonPath("$.userDto.email").value("test@test.com"))
			.andExpect(jsonPath("$.userDto.name").value("사용자"))
			.andExpect(jsonPath("$.userDto.role").value("USER"))
			.andExpect(jsonPath("$.userDto.locked").value(false))
			.andExpect(jsonPath("$.accessToken").value(accessToken));

		verify(refreshTokenCookieWriter).write(
			any(HttpServletResponse.class),
			eq(newRefreshToken)
		);
	}

	@Test
	@DisplayName("토큰 재발급 성공 시 새 refresh token 쿠키를 내려준다")
	void refresh_writeRefreshTokenCookie_whenRefreshSucceeds() throws Exception {
		// given
		String refreshToken = "old-refresh-token";
		String newRefreshToken = "new-refresh-token";
		String accessToken = "new-access-token";

		UserDto userDto = new UserDto(
			UUID.randomUUID(),
			Instant.parse("2026-06-29T01:00:00Z"),
			"test@test.com",
			"사용자",
			null,
			UserRole.USER,
			false
		);
		JwtDto jwtDto = new JwtDto(userDto, accessToken);

		given(jwtProperties.refreshTokenCookieName()).willReturn("REFRESH_TOKEN");
		given(authTokenService.refresh(refreshToken))
			.willReturn(new TokenRefreshResult(jwtDto, newRefreshToken));

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("REFRESH_TOKEN", refreshToken)))
			.andExpect(status().isOk());

		verify(refreshTokenCookieWriter).write(
			any(MockHttpServletResponse.class),
			eq(newRefreshToken)
		);
	}

	@Test
	@DisplayName("refresh token 쿠키가 없으면 401 Unauthorized를 반환한다")
	void refresh_returnUnauthorized_whenRefreshTokenCookieIsMissing() throws Exception {
		// given
		given(jwtProperties.refreshTokenCookieName()).willReturn("REFRESH_TOKEN");
		given(authTokenService.refresh(null))
			.willThrow(new AuthException(AuthErrorCode.MISSING_REFRESH_TOKEN));

		// when & then
		mockMvc.perform(post("/api/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.exceptionName").value("AuthException"))
			.andExpect(jsonPath("$.message").value("refresh token이 없습니다."));

		verify(refreshTokenCookieWriter, never()).write(
			any(HttpServletResponse.class),
			any()
		);
	}

	@Test
	@DisplayName("쿠키 이름이 다르면 refresh token이 없는 것으로 처리한다")
	void refresh_returnUnauthorized_whenRefreshTokenCookieNameDoesNotMatch() throws Exception {
		// given
		given(jwtProperties.refreshTokenCookieName()).willReturn("REFRESH_TOKEN");
		given(authTokenService.refresh(null))
			.willThrow(new AuthException(AuthErrorCode.MISSING_REFRESH_TOKEN));

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("OTHER_TOKEN", "refresh-token"))
				.header(HttpHeaders.ACCEPT, "*/*"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.exceptionName").value("AuthException"))
			.andExpect(jsonPath("$.message").value("refresh token이 없습니다."));

		verify(refreshTokenCookieWriter, never()).write(
			any(HttpServletResponse.class),
			any()
		);
	}
}