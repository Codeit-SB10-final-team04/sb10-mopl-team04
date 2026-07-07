package com.team04.mopl.notification.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

	NOTIFICATION_INVALID_INPUT(HttpStatus.BAD_REQUEST, "NO01", "잘못된 입력값입니다."),
	NOTIFICATION_RECEIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "NO02", "알림 수신자를 찾을 수 없습니다."),
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NO03", "존재하지 않는 알림입니다.");

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
