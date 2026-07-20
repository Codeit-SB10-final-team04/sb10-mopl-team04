package com.team04.mopl.conversation.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ConversationErrorCode implements ErrorCode {
	CONVERSATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CV01", "이미 해당 사용자와의 대화방이 존재합니다."),
	CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CV02", "해당 대화의 정보를 찾을 수 없습니다."),
	CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CV03", "대화 참여자만 대화방에 접근할 수 있습니다."),
	CONVERSATION_SELF_SELECT_MOT_ALLOWED(HttpStatus.BAD_REQUEST, "CV04", "자기 자신과의 대화방은 조회할 수 없습니다."),
	CONVERSATION_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "CV05", "잘못된 형태의 값이 입력되었습니다."),
	CONVERSATION_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "CV06", "해당 대화에서 해당 참여자의 정보를 찾을 수 없습니다.");

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
