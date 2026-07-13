package com.team04.mopl.directmessage.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DirectMessageSendRequest(
	@NotBlank(message = "송신할 메시지 내용을 입력해주세요.")
	String content
) {
}
