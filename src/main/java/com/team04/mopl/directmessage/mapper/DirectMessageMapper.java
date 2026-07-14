package com.team04.mopl.directmessage.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface DirectMessageMapper {

	// DirectMessageRequest -> DirectMessage
	@Mapping(target = "content", source = "directMessageSendRequest.content")
	DirectMessage toEntity(
		Conversation conversation,
		User sender,
		User receiver,
		DirectMessageSendRequest directMessageSendRequest
	);

	// DirectMessage -> DirectMessageDto
	@Mapping(target = "conversationId", source = "conversation.id")
	@Mapping(target = "sender", expression = "java(toUserSummary(directMessage.getSender()))")
	@Mapping(target = "receiver", expression = "java(toUserSummary(directMessage.getReceiver()))")
	DirectMessageDto toDto(DirectMessage directMessage);

	// DirectMessageList -> DirectMessageDto
	List<DirectMessageDto> toDtoList(List<DirectMessage> directMessages);

	// DirectMessageDto -> CursorResponseDirectMessageDto
	default CursorResponseDirectMessageDto toCursorPageResponse(
		List<DirectMessageDto> data,
		String nextCursor,
		UUID nextIdAfter,
		boolean hasNext,
		Long totalCount,
		String sortBy,
		String sortDirection
	) {
		return CursorResponseDirectMessageDto.builder()
			.data(data)
			.nextCursor(nextCursor)
			.nextIdAfter(nextIdAfter)
			.hasNext(hasNext)
			.totalCount(totalCount)
			.sortBy(sortBy)
			.sortDirection(sortDirection)
			.build();
	}

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
