package com.team04.mopl.conversation.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ConversationErrorCode implements ErrorCode {
	CONVERSATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CV01", "이미 해당 사용자와의 대화방이 존재합니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String description;

	@Override
	public HttpStatus getHttpStatus() {
		return null;
	}

	@Override
	public String getCode() {
		return "";
	}

	@Override
	public String getMessage() {
		return "";
	}
}
