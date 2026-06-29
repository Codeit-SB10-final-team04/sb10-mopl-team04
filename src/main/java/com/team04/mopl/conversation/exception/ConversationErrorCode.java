package com.team04.mopl.conversation.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ConversationErrorCode implements ErrorCode {
	CONVERSATION_CANNOT_CHAT_WITH_SELF(HttpStatus.BAD_REQUEST, "CV01", "본인과의 채팅방을 만들 수 없습니다.");

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
