package com.team04.mopl.directmessage.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;

import lombok.Builder;

@Builder
public record DirectMessageDto(
	UUID id,
	UUID conversationId,
	Instant createdAt,
	UserSummary sender,
	UserSummary receiver,
	String content
) {
}
