package com.team04.mopl.notification.kafka.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum KafkaEventErrorCode implements ErrorCode {

	KAFKA_EVENT_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "KA01", "Kafka Event 직렬화에 실패했습니다."),
	KAFKA_EVENT_DESERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "KA02", "Kafka Event 역직렬화에 실패했습니다.");

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
