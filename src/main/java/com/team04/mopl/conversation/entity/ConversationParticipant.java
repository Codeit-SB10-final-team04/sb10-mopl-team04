package com.team04.mopl.conversation.entity;

import java.time.Instant;

import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "conversation_participants")
public class ConversationParticipant {

	@EmbeddedId
	// 복합 PK (대화 Id, 사용자 Id)
	private ConversationParticipantId id;

	@MapsId("conversationId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "last_read_at")
	private Instant lastReadAt;

	@Builder
	public ConversationParticipant(Conversation conversation, User user) {
		this.id = new ConversationParticipantId(conversation.getId(), user.getId());
		this.conversation = conversation;
		this.user = user;
		this.lastReadAt = null;
	}

	private void updateLastReadAt() {
		this.lastReadAt = Instant.now();
	}
}
