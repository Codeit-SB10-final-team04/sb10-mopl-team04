package com.team04.mopl.conversation.repository.qdsl;

import java.util.List;

import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.entity.Conversation;

public class ConversationQdslRepositoryImpl implements ConversationQdslRepository {
	@Override
	public List<Conversation> searchConversation(ConversationPageRequest conversationPageRequest) {
		return List.of();
	}

	@Override
	public Long countConversation(ConversationPageRequest conversationPageRequest) {
		return 0L;
	}
}
