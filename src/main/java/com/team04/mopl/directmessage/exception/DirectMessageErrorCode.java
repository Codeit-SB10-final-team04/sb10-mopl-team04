package com.team04.mopl.directmessage.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DirectMessageErrorCode implements ErrorCode {
	DM_BLANK(HttpStatus.BAD_REQUEST, "DM01", "메시지 내용은 공백일 수 없습니다."),
	DM_NOT_FOUND(HttpStatus.NOT_FOUND, "DM02", "해당 메시지의 정보를 찾을 수 없습니다."),
	DM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DM03", "수신자 및 송신자만 자신의 DM에 접근할 수 있습니다."),
	DM_NOT_IN_CONVERSATION(HttpStatus.BAD_REQUEST, "DM04", "해당 대화방에 존재하지 않는 메시지입니다.");

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
