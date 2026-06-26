package com.team04.mopl.conversation.dto.response;

import java.util.UUID;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;

import lombok.Builder;

@Builder
public record ConversationDto(
	UUID id,
	// TODO: 임시로 PlaylistUserSummary로 구현, 해당 파일 common으로 이동하면 UserSummary로 대체할 에정
	PlaylistUserSummary with,
	DirectMessageDto lastestMessage,
	boolean hasUnread
) {
}
