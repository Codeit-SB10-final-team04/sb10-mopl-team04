package com.team04.mopl.notification.kafka.exception;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class KafkaEventException extends MoplException {

	public KafkaEventException(ErrorCode errorCode) {
		super(errorCode);
	}

	public KafkaEventException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
