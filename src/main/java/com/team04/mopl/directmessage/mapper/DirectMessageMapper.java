package com.team04.mopl.directmessage.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface DirectMessageMapper {

	// DirectMessage -> DirectMessageDto
	@Mapping(target = "conversationId", source = "conversation.id")
	@Mapping(target = "sender", expression = "java(toUserSummary(directMessage.getSender()))")
	@Mapping(target = "receiver", expression = "java(toUserSummary(directMessage.getReceiver()))")
	DirectMessageDto toDto(DirectMessage directMessage);

	// User -> UserSummary 변환
	default UserSummary toUserSummary(User user) {
		if (user == null) {
			return null;
		}
		return new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}
}
