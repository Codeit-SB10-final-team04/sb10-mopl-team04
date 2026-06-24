package com.team04.mopl.conversation.entity;

import java.time.Instant;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "conversation_participants",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_conversation_user",
			columnNames = {"conversation_id", "user_id"}
		)
	}
)
public class ConversationParticipant extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "last_read_at")
	private Instant lastReadAt;

	@Builder
	public ConversationParticipant(Conversation conversation, User user) {
		this.conversation = conversation;
		this.user = user;
		this.lastReadAt = null;
	}

	private void updateLastReadAt() {
		this.lastReadAt = Instant.now();
	}
}
