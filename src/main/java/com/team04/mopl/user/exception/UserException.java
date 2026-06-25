package com.team04.mopl.user.exception;

import java.util.Map;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class UserException extends MoplException {

	public UserException(ErrorCode errorCode) {
		super(errorCode);
	}

	public UserException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode);

		if (details == null) {
			return;
		}

		details.forEach(this::addDetail);
	}
}
