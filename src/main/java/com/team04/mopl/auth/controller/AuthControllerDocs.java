package com.team04.mopl.auth.controller;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.dto.response.JwtDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "인증 관리", description = "인증 API")
public interface AuthControllerDocs {

	@Operation(summary = "토큰 재발급", description = "쿠키(REFRESH_TOKEN)에 저장된 리프레시 토큰으로 리프레시 토큰과 엑세스 토큰을 재발급합니다.")
	ResponseEntity<JwtDto> refresh(HttpServletRequest request, HttpServletResponse response);

	@Operation(summary = "CSRF 토큰 조회", description = "CSRF 토큰을 조회합니다. 토큰은 쿠키(XSRF-TOKEN)에 저장됩니다.")
	ResponseEntity<Void> getCsrfToken();
}