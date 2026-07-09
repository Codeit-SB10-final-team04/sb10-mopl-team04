package com.team04.mopl.content.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ContentChatSendRequest(

	@NotBlank(message = "채팅 내용은 비어있을 수 없습니다.")
	String content
) {
}
