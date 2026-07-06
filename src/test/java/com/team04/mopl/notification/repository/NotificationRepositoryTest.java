package com.team04.mopl.notification.repository;

import static org.assertj.core.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:notification-restore-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-notification-restore-test.sql"
})
@Import(QuerydslConfig.class)
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	UUID receiverId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	UUID receiverId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	UUID lastNotificationId = UUID.fromString("10000000-0000-0000-0000-000000000003");
	UUID sameTimeBeforeId = UUID.fromString("10000000-0000-0000-0000-000000000002");
	UUID sameTimeAfterId = UUID.fromString("10000000-0000-0000-0000-000000000004");
	UUID laterWithSmallerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
	UUID readNotificationId = UUID.fromString("10000000-0000-0000-0000-000000000005");
	UUID otherUserNotificationId = UUID.fromString("10000000-0000-0000-0000-000000000006");
	UUID olderNotificationId = UUID.fromString("10000000-0000-0000-0000-000000000007");

	Instant lastNotificationCreatedAt = Instant.parse("2026-06-24T02:00:00Z");

	@BeforeEach
	void setUp() {
		insertUser(receiverId1, "receiver1");
		insertUser(receiverId2, "receiver2");

		insertNotification(olderNotificationId, receiverId1, null, Instant.parse("2026-06-24T01:00:00Z"));
		insertNotification(sameTimeBeforeId, receiverId1, null, lastNotificationCreatedAt);
		insertNotification(lastNotificationId, receiverId1, null, lastNotificationCreatedAt);
		insertNotification(sameTimeAfterId, receiverId1, null, lastNotificationCreatedAt);
		insertNotification(laterWithSmallerId, receiverId1, null, Instant.parse("2026-06-24T03:00:00Z"));
		insertNotification(readNotificationId, receiverId1, Instant.parse("2026-06-24T04:00:00Z"),
			Instant.parse("2026-06-24T03:30:00Z"));
		insertNotification(otherUserNotificationId, receiverId2, null, Instant.parse("2026-06-24T05:00:00Z"));
	}

	@Test
	@DisplayName("lastEventId 이후의 현재 사용자의 미읽음 알림만 생성일 오름차순, id 오름차순으로 조회한다.")
	void findUnreadNotificationsAfter_returnUnreadNotifications_whenAfterLastEventId() {
		// when
		List<Notification> result = notificationRepository.findUnreadNotificationsAfter(
			receiverId1,
			lastNotificationId,
			lastNotificationCreatedAt,
			PageRequest.of(0, 10)
		);

		// then
		assertThat(result)
			.extracting(notification -> notification.getId())
			.containsExactly(sameTimeAfterId, laterWithSmallerId);
	}

	@Test
	@DisplayName("복구 알림 조회 시 Pageable 크기만큼 조회한다.")
	void findUnreadNotificationsAfter_returnLimitUnreadNotifications_whenAfterLastEventId() {
		// when
		List<Notification> result = notificationRepository.findUnreadNotificationsAfter(
			receiverId1,
			lastNotificationId,
			lastNotificationCreatedAt,
			PageRequest.of(0, 1)
		);

		// then
		assertThat(result)
			.extracting(notification -> notification.getId())
			.containsExactly(sameTimeAfterId);
	}

	private void insertUser(UUID userId, String name) {
		jdbcTemplate.update("""
				INSERT INTO users (
				    id, name, email, email_type, password_hash, profile_image_url,
				    role, is_locked, created_at, updated_at
				)
				VALUES (?, ?, ?, 'REAL', NULL, ?, 'USER', FALSE, ?, ?)
				""",
			userId,
			name,
			name + "@test.com",
			"https://example.com/" + name,
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z")),
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z"))
		);
	}

	private void insertNotification(
		UUID notificationId,
		UUID receiverId,
		Instant readAt,
		Instant createdAt
	) {
		jdbcTemplate.update("""
				INSERT INTO notifications (
				    id, receiver_id, title, content, type, level, read_at, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
			notificationId,
			receiverId,
			"알림 제목",
			"알림 내용",
			NotificationType.DM.toString(),
			NotificationLevel.INFO.toString(),
			readAt == null ? null : Timestamp.from(readAt),
			Timestamp.from(createdAt)
		);
	}
}