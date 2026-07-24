package com.team04.mopl.notification.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	name = "notifications",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_notifications_source_event_receiver",
			columnNames = {"source_event_id", "receiver_id"}
		)
	}
)
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_id", nullable = false)
	private User receiver;

	@Column(name = "source_event_id")
	private UUID sourceEventId;

	@Column(name = "title", nullable = false, length = 50)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "level", nullable = false, length = 20)
	private NotificationLevel level;

	@Column(name = "read_at", nullable = true)
	private Instant readAt;

	@Builder
	protected Notification(
		User receiver,
		UUID sourceEventId,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		this.receiver = receiver;
		this.sourceEventId = sourceEventId;
		this.title = title;
		this.content = content;
		this.type = type;
		this.level = level;

		this.readAt = null;
	}

	// 읽음 상태로 mark
	public void markRead(Instant readAt) {
		if (this.readAt == null) {
			this.readAt = Objects.requireNonNull(readAt);
		}
	}
}
