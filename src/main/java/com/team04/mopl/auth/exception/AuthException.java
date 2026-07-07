package com.team04.mopl.auth.exception;

import java.util.Map;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class AuthException extends MoplException {

	public AuthException(ErrorCode errorCode) {
		super(errorCode);
	}

	public AuthException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}

	public AuthException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode);

		if (details == null) {
			return;
		}

		details.forEach(this::addDetail);
	}
}
