package com.team04.mopl.auth.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AU01", "이메일 또는 비밀번호가 올바르지 않습니다."),
	LOCKED_ACCOUNT(HttpStatus.UNAUTHORIZED, "AU02", "잠긴 계정입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AU03", "인증이 필요합니다."),
	INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU04", "유효하지 않은 access token입니다."),
	EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU05", "만료된 access token입니다."),
	AUTH_SESSION_INVALID(HttpStatus.UNAUTHORIZED, "AU06", "인증 세션이 유효하지 않습니다."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "AU07", "접근 권한이 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
