package com.team04.mopl.auth.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AU01", "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_LOCKED_ACCOUNT(HttpStatus.UNAUTHORIZED, "AU02", "잠긴 계정입니다."),
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AU03", "인증이 필요합니다."),
	AUTH_INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU04", "유효하지 않은 access token입니다."),
	AUTH_EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AU05", "만료된 access token입니다."),
	AUTH_SESSION_INVALID(HttpStatus.UNAUTHORIZED, "AU06", "인증 세션이 유효하지 않습니다."),
	AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AU07", "접근 권한이 없습니다."),
	AUTH_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AU08", "인증 처리 중 오류가 발생했습니다."),
	AUTH_MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU09", "refresh token이 없습니다."),
	AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU10", "유효하지 않은 refresh token입니다."),
	AUTH_EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU11", "만료된 refresh token입니다."),
	AUTH_SESSION_REQUIRED_VALUE(HttpStatus.INTERNAL_SERVER_ERROR, "AU12", "인증 세션 필수값이 누락되었습니다."),
	AUTH_TOKEN_EXPIRATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AU13", "토큰 만료 시각이 올바르지 않습니다."),
	AUTH_MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AU14", "임시 비밀번호 이메일 전송에 실패했습니다."),
	AUTH_TEMPORARY_PASSWORD_REQUIRED_VALUE(HttpStatus.INTERNAL_SERVER_ERROR, "AU15", "임시 비밀번호 필수값이 누락되었습니다."),
	AUTH_TEMPORARY_PASSWORD_EXPIRATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AU16", "임시 비밀번호 만료 시각이 올바르지 않습니다.");

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
