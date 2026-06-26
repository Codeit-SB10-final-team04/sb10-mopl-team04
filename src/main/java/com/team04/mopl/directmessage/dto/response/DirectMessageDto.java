package com.team04.mopl.directmessage.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.user.entity.User;

import lombok.Builder;

@Builder
public record DirectMessageDto(
	UUID id,
	UUID conversationId,
	Instant createdAt,
	User sender,
	User receiver,
	String content
) {
}
