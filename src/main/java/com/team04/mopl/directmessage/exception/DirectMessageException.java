package com.team04.mopl.directmessage.exception;

import com.team04.mopl.common.exception.MoplException;

public class DirectMessageException extends MoplException {
	public DirectMessageException(DirectMessageErrorCode errorCode) {
		super(errorCode);
	}

	public DirectMessageException(DirectMessageErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
