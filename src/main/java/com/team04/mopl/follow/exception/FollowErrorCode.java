package com.team04.mopl.follow.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {
	FOLLOW_ALREADY(HttpStatus.CONFLICT, "FW01", "이미 해당 사용자를 팔로우 한 상태입니다."),
	FOLLOW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FW02", "본인을 팔로우 할 수 없습니다."),
	FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "FW03", "해당 팔로우의 정보를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String description;

	@Override
	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

	@Override
	public String getCode() {
		return this.code;
	}

	@Override
	public String getMessage() {
		return this.description;
	}
}
