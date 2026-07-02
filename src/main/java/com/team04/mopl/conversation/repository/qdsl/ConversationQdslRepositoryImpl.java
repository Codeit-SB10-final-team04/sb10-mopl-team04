package com.team04.mopl.conversation.repository.qdsl;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.entity.Conversation;

// TODO: 추후 구현하여, 대화 목록 조회 PR에 포함할 예정
public class ConversationQdslRepositoryImpl implements ConversationQdslRepository {
	@Override
	public List<Conversation> searchConversation(
		ConversationPageRequest conversationPageRequest,
		UUID requestUserId
	) {
		return List.of();
	}

	@Override
	public Long countConversation(
		ConversationPageRequest conversationPageRequest,
		UUID requestUserId
	) {
		return 0L;
	}
}
