package com.team04.mopl.notification.exception;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class NotificationException extends MoplException {

	public NotificationException(ErrorCode errorCode) {
		super(errorCode);
	}

	public NotificationException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
