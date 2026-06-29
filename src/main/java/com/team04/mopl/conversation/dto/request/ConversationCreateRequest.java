package com.team04.mopl.conversation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ConversationCreateRequest(
	@NotNull(message = "채팅하고자 하는 사용자의 ID를 입력해주세요.")
	UUID withUserId
) {
}
