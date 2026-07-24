package com.team04.mopl.conversation.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ConversationCreateRequest(
	@Schema(description = "대화 상대 정보 ID")
	@NotNull(message = "채팅하고자 하는 사용자의 ID를 입력해주세요.")
	UUID withUserId
) {
}
