package com.team04.mopl.user.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
	EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "US01", "이미 사용 중인 이메일입니다."),
	NAME_REQUIRED(HttpStatus.BAD_REQUEST, "US02", "사용자 이름은 필수입니다."),
	EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "US03", "이메일은 필수입니다."),
	PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "US04", "비밀번호는 필수입니다."),
	ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "US05", "권한은 필수입니다."),
	SOCIAL_ACCOUNT_USER_REQUIRED(HttpStatus.BAD_REQUEST, "US06", "사용자는 필수입니다."),
	SOCIAL_PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "US07", "소셜 제공자는 필수입니다."),
	SOCIAL_PROVIDER_USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "US08", "소셜 사용자 ID는 필수입니다.");


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
