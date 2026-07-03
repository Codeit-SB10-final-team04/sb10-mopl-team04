package com.team04.mopl.review.exception;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class ReviewException extends MoplException {

	public ReviewException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ReviewException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
