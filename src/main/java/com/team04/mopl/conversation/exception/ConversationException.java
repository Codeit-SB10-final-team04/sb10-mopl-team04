package com.team04.mopl.conversation.exception;

import com.team04.mopl.common.exception.MoplException;

public class ConversationException extends MoplException {
	public ConversationException(ConversationErrorCode errorCode) {
		super(errorCode);
	}
}
