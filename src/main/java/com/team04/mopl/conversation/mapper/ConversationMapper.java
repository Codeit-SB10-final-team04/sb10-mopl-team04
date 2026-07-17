package com.team04.mopl.conversation.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

@Mapper(config = MapStructConfig.class)
public interface ConversationMapper {

	// Conversation Entity -> ConversationDto
	@Mapping(target = "id", source = "conversation.id")
	ConversationDto toDto(
		Conversation conversation,
		UserSummary with,
		DirectMessageDto lastestMessage,
		boolean hasUnread
	);

	// ConversationDto -> CursorResponseConversationDto
	default CursorResponseConversationDto toCursorPageResponse(
		List<ConversationDto> data,
		String nextCursor,
		UUID nextIdAfter,
		boolean hasNext,
		Long totalCount,
		String sortBy,
		String sortDirection
	) {
		return CursorResponseConversationDto.builder()
			.data(data)
			.nextCursor(nextCursor)
			.nextIdAfter(nextIdAfter)
			.hasNext(hasNext)
			.totalCount(totalCount)
			.sortBy(sortBy)
			.sortDirection(sortDirection)
			.build();
	}
}
