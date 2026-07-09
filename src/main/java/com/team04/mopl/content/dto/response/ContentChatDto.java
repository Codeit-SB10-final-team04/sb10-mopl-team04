package com.team04.mopl.content.dto.response;

import com.team04.mopl.common.dto.UserSummary;

public record ContentChatDto(
	UserSummary sender,
	String content
) {
}
