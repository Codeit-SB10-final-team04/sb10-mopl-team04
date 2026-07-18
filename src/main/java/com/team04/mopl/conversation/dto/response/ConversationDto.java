package com.team04.mopl.conversation.dto.response;

import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

import lombok.Builder;

@Builder
public record ConversationDto(
	UUID id,
	UserSummary with,
	DirectMessageDto lastestMessage,
	boolean hasUnread
) {
}
