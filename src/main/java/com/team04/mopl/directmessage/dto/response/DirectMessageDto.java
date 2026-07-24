package com.team04.mopl.directmessage.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record DirectMessageDto(
	@Schema(description = "메시지 ID")
	UUID id,

	@Schema(description = "대화 ID")
	UUID conversationId,

	@Schema(description = "메시지 생성 시간")
	Instant createdAt,

	@Schema(description = "발신자 정보")
	UserSummary sender,

	@Schema(description = "수신자 정보")
	UserSummary receiver,

	@Schema(description = "메시지 내용")
	String content
) {
}
