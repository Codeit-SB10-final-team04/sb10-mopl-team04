package com.team04.mopl.user.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
	USER_EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "US01", "이미 사용 중인 이메일입니다."),
	USER_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "US02", "사용자 이름은 필수입니다."),
	USER_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "US03", "이메일은 필수입니다."),
	USER_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "US04", "비밀번호는 필수입니다."),
	USER_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "US05", "권한은 필수입니다."),
	USER_SOCIAL_ACCOUNT_USER_REQUIRED(HttpStatus.BAD_REQUEST, "US06", "사용자는 필수입니다."),
	USER_SOCIAL_PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "US07", "소셜 제공자는 필수입니다."),
	USER_SOCIAL_PROVIDER_USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "US08", "소셜 사용자 ID는 필수입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "US09", "사용자를 찾을 수 없습니다."),
	USER_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "US10", "커서 값이 올바르지 않습니다."),
	USER_LOCKED_REQUIRED(HttpStatus.BAD_REQUEST, "US11", "잠금 상태는 필수입니다."),
	USER_PROFILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "US12", "본인의 프로필만 변경할 수 있습니다."),
	USER_PASSWORD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "US13", "본인의 비밀번호만 변경할 수 있습니다.");

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
