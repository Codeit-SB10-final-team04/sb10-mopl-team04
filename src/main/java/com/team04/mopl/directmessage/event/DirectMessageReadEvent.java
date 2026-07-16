package com.team04.mopl.directmessage.event;

import java.util.UUID;

public record DirectMessageReadEvent(
	UUID receiverId,
	UUID conversationId,
	UUID directMessageId
) {
}
