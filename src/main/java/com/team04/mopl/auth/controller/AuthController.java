package com.team04.mopl.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.dto.request.ResetPasswordRequest;
import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.service.TemporaryPasswordService;
import com.team04.mopl.auth.security.cookie.RefreshTokenCookieWriter;
import com.team04.mopl.auth.security.jwt.JwtProperties;
import com.team04.mopl.auth.service.AuthTokenService;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

	private final AuthTokenService authTokenService;
	private final RefreshTokenCookieWriter refreshTokenCookieWriter;
	private final JwtProperties jwtProperties;
	private final TemporaryPasswordService temporaryPasswordService;

	// refresh token 쿠키로 access token과 refresh token을 재발급
	@Override
	@PostMapping("/api/auth/refresh")
	public ResponseEntity<JwtDto> refresh(
		HttpServletRequest request,
		HttpServletResponse response
	) {
		String refreshToken = resolveRefreshTokenFromCookie(request);

		TokenRefreshResult result = authTokenService.refresh(refreshToken);

		refreshTokenCookieWriter.write(response, result.refreshToken());

		return ResponseEntity.status(HttpStatus.OK).body(result.jwtDto());
	}

	// CSRF 토큰 조회
	@Override
	@GetMapping("/api/auth/csrf-token")
	public ResponseEntity<Void> getCsrfToken() {
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	// 이메일 주소로 임시 비밀번호를 발급하고 이메일로 전송
	@Override
	@PostMapping("/api/auth/reset-password")
	public ResponseEntity<Void> resetPassword(
		@Valid @RequestBody ResetPasswordRequest request
	) {
		temporaryPasswordService.resetPassword(request.email());

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	// 요청 쿠키에서 refresh token 값을 추출
	private String resolveRefreshTokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			return null;
		}

		for (Cookie cookie : cookies) {
			if (jwtProperties.refreshTokenCookieName().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}

		return null;
	}
}
