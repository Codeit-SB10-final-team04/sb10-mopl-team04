package com.team04.mopl.content.exception;

import java.util.Map;

import com.team04.mopl.common.exception.ErrorCode;
import com.team04.mopl.common.exception.MoplException;

public class ContentException extends MoplException {

	public ContentException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ContentException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode);

		if (details == null) {
			return;
		}

		details.forEach((key, value) -> {
			if (key != null && value != null) {
				addDetail(key, value);
			}
		});
	}
}
