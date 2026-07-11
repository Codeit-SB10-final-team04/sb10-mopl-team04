package com.team04.mopl.directmessage.event;

import java.util.UUID;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

public record DirectMessageCreatedEvent(
	UUID receiverId,
	UUID directMessageId,
	DirectMessageDto directMessageDto
) {
}