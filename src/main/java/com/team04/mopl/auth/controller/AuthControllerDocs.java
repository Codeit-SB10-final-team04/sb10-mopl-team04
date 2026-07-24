package com.team04.mopl.auth.controller;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.dto.request.ResetPasswordRequest;
import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.common.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "인증 관리", description = "인증 API")
public interface AuthControllerDocs {

	@Operation(summary = "토큰 재발급", description = "쿠키(REFRESH_TOKEN)에 저장된 리프레시 토큰으로 리프레시 토큰과 액세스 토큰을 재발급합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<JwtDto> refresh(
		@Parameter(hidden = true) HttpServletRequest request,
		@Parameter(hidden = true) HttpServletResponse response
	);

	@Operation(summary = "CSRF 토큰 조회", description = "CSRF 토큰을 조회합니다. 토큰은 쿠키(XSRF-TOKEN)에 저장됩니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "성공"),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> getCsrfToken();

	@Operation(summary = "비밀번호 초기화", description = "임시 비밀번호를 발급하여 이메일로 전송합니다")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "해당 리소스 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> resetPassword(ResetPasswordRequest request);
}
