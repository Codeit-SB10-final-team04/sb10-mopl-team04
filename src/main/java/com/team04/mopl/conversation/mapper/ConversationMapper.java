package com.team04.mopl.conversation.mapper;

import org.mapstruct.Mapper;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface ConversationMapper {

	// Conversation Entity -> ConversationDto
	ConversationDto toDto(Conversation conversation, User with, DirectMessageDto lastestMessage);
}
