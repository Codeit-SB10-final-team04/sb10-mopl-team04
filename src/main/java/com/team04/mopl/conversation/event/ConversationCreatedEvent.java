package com.team04.mopl.conversation.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationCreatedEvent(
	UUID conversationId,
	List<UUID> participantIds,
	Instant createdAt
) {
}
