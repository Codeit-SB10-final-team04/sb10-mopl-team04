package com.team04.mopl.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.auth.security.jwt.JwtProperties;
import com.team04.mopl.auth.service.AuthTokenService;
import com.team04.mopl.auth.service.TemporaryPasswordService;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.common.exception.GlobalExceptionHandler;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

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

	@MockitoBean
	private TemporaryPasswordService temporaryPasswordService;

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
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value(accessToken))
			.andExpect(jsonPath("$.userDto.email").value("test@test.com"));

		verify(authTokenService).refresh(refreshToken);
		verify(refreshTokenCookieWriter).write(
			any(HttpServletResponse.class),
			eq(newRefreshToken)
		);
	}

	@Test
	@DisplayName("refresh token 쿠키가 없으면 401 Unauthorized를 반환한다")
	void refresh_returnUnauthorized_whenRefreshTokenCookieIsMissing() throws Exception {
		// given
		given(jwtProperties.refreshTokenCookieName()).willReturn("REFRESH_TOKEN");
		given(authTokenService.refresh(null))
			.willThrow(new AuthException(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN));

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
			.willThrow(new AuthException(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN));

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

	@Test
	@DisplayName("CSRF 토큰 조회 요청에 성공하면 204 No Content를 반환한다")
	void getCsrfToken_returnNoContent_whenRequested() throws Exception {
		// when & then
		mockMvc.perform(get("/api/auth/csrf-token"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verifyNoInteractions(authTokenService);
		verifyNoInteractions(refreshTokenCookieWriter);
	}

	@Test
	@DisplayName("비밀번호 초기화 요청에 성공하면 204 No Content를 반환한다")
	void resetPassword_returnNoContent_whenRequestValid() throws Exception {
		// given
		String email = "user@example.com";

		// when, then
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": "user@example.com"
					}
					"""))
			.andExpect(status().isNoContent());

		verify(temporaryPasswordService).resetPassword(email);
	}

	@Test
	@DisplayName("이메일이 비어 있으면 400 Bad Request를 반환한다")
	void resetPassword_returnBadRequest_whenEmailBlank() throws Exception {
		// when, then
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": ""
					}
					"""))
			.andExpect(status().isBadRequest());

		verify(temporaryPasswordService, never()).resetPassword("");
	}

	@Test
	@DisplayName("이메일 형식이 올바르지 않으면 400 Bad Request를 반환한다")
	void resetPassword_returnBadRequest_whenEmailInvalid() throws Exception {
		// when, then
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": "invalid-email"
					}
					"""))
			.andExpect(status().isBadRequest());

		verify(temporaryPasswordService, never()).resetPassword("invalid-email");
	}

	@Test
	@DisplayName("존재하지 않는 이메일이면 404 Not Found를 반환한다")
	void resetPassword_returnNotFound_whenUserNotFound() throws Exception {
		// given
		String email = "unknown@example.com";

		doThrow(new UserException(UserErrorCode.USER_NOT_FOUND))
			.when(temporaryPasswordService)
			.resetPassword(email);

		// when, then
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": "unknown@example.com"
					}
					"""))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("비밀번호 초기화 처리 중 메일 발송에 실패하면 500 Internal Server Error를 반환한다")
	void resetPassword_returnInternalServerError_whenMailSendFailed() throws Exception {
		// given
		String email = "user@example.com";

		doThrow(new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED))
			.when(temporaryPasswordService)
			.resetPassword(email);

		// when, then
		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": "user@example.com"
					}
					"""))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.exceptionName").value("AuthException"))
			.andExpect(jsonPath("$.message").value("임시 비밀번호 이메일 전송에 실패했습니다."));
	}
}