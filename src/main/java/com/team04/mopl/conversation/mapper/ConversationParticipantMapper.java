package com.team04.mopl.follow.mapper;

import org.mapstruct.Mapper;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface ConversationParticipantMapper {

	// ConversationParticipant 변환
	ConversationParticipant toEntity(Conversation conversation, User user);
}
