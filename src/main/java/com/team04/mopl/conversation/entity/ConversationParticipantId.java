package com.team04.mopl.conversation.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
	ConversationParticipantId
	-------------------------
	대화 참여자(conversation_participant)의 복합 PK
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ConversationParticipantId implements Serializable {
	private UUID conversationId;
	private UUID userId;

	@Builder
	public ConversationParticipantId(UUID conversationId, UUID userId) {
		this.conversationId = conversationId;
		this.userId = userId;
	}
}
