package com.team04.mopl.conversation.mapper;

import org.mapstruct.Mapper;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;

@Mapper(config = MapStructConfig.class)
public interface ConversationMapper {

	// Conversation Entity -> ConversationDto
	ConversationDto toDto(Conversation conversation, UserSummary with, DirectMessageDto lastestMessage);
}
