package com.team04.mopl.follow.exception;

import com.team04.mopl.common.exception.MoplException;

public class FollowException extends MoplException {
	public FollowException(FollowErrorCode errorCode) {
		super(errorCode);
	}
}
