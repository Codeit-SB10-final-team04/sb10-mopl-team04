package com.team04.mopl.directmessage.event;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

public record DirectMessageCreatedEvent(

	UUID eventId,

	UUID receiverId,
	UUID directMessageId,
	DirectMessageDto directMessageDto,

	// 이벤트 발생 시간
	Instant occurredAt
) {
	public static DirectMessageCreatedEvent of(
		UUID receiverId,
		UUID directMessageId,
		DirectMessageDto directMessageDto
	) {
		return new DirectMessageCreatedEvent(
			UUID.randomUUID(),
			receiverId,
			directMessageId,
			directMessageDto,
			Instant.now()
		);
	}
}
