package com.team04.mopl.directmessage.entity;

import java.time.Instant;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "direct_messages")
public class DirectMessage extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_id", nullable = false)
	private User receiver;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "read_at")
	private Instant readAt;

	@Builder
	public DirectMessage(
		User sender,
		User receiver,
		Conversation conversation,
		String content
	) {
		validateContent(content);

		this.sender = sender;
		this.receiver = receiver;
		this.conversation = conversation;
		this.content = content;
		this.read = false;        // 기본 상태: 안 읽음
		this.readAt = null;
	}

	// 읽음 상태로 전환 (역은 성립하지 않음)
	public void markAsRead() {
		// 이미 읽음 상태일 경우, 덮어쓰기 방지를 위하여 반환
		if (this.read) {
			return;
		}

		this.read = true;
		this.readAt = Instant.now();
	}

	// 유효성 검증: 메시지 공백
	private void validateContent(String content) {
		if (content == null || content.isBlank()) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_BLANK);
		}
	}
}
