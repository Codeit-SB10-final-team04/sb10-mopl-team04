package com.team04.mopl.conversation.dto.response;

import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record ConversationDto(
	@Schema(description = "대화 ID")
	UUID id,

	@Schema(description = "대화 상대 정보")
	UserSummary with,

	@Schema(description = "마지막 메시지 내용")
	DirectMessageDto lastestMessage,

	@Schema(description = "읽지 않은 메시지 존재 여부")
	boolean hasUnread
) {
}
