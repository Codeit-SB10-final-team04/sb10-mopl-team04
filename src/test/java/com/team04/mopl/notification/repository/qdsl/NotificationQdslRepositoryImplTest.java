package com.team04.mopl.notification.repository.qdsl;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.notification.dto.request.NotificationSearchRequest;
import com.team04.mopl.notification.dto.response.NotificationCursorPage;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationSortBy;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.exception.NotificationException;
import com.team04.mopl.notification.repository.NotificationRepository;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:notification-querydsl-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-notification_querydsl-test.sql"
})
@Import(QuerydslConfig.class)
class NotificationQdslRepositoryImplTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	UUID receiver1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	UUID receiver2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	UUID notification1 = UUID.fromString("30000000-0000-0000-0000-000000000003");
	UUID notification2 = UUID.fromString("30000000-0000-0000-0000-000000000005");
	UUID notification3 = UUID.fromString("30000000-0000-0000-0000-000000000004");
	UUID notification4 = UUID.fromString("30000000-0000-0000-0000-000000000001");
	UUID readNotification = UUID.fromString("30000000-0000-0000-0000-000000000006");
	UUID otherUserNotification = UUID.fromString("30000000-0000-0000-0000-000000000007");

	Instant createdAt1 = Instant.parse("2026-06-24T03:00:00Z");
	Instant createdAt2 = Instant.parse("2026-06-24T02:00:00Z");
	Instant createdAt3 = Instant.parse("2026-06-24T01:00:00Z");
	Instant readAt = Instant.parse("2026-06-24T04:00:00Z");

	@BeforeEach
	void setUp() {
		insertFixtures();
	}

	@Test
	@DisplayName("현재 사용자의 읽지 않은 알림을 생성일 내림차순으로 조회한다.")
	void findAllNotifications_returnCursorPage_whenOrderByCreatedAtDesc() {
		// given
		NotificationSearchRequest request = new NotificationSearchRequest(
			null, null,
			3,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		// when
		NotificationCursorPage result =
			notificationRepository.findAllNotifications(request, receiver1);

		// then
		assertEquals(3, result.notificationList().size());
		assertThat(result.notificationList())
			.extracting(notification -> notification.getId())
			.containsExactly(notification1, notification2, notification3);
		assertThat(result.notificationList())
			.extracting(notification -> notification.getReceiver().getId())
			.containsOnly(receiver1);
		assertTrue(result.hasNext());
		assertEquals(4L, result.totalCount());

	}

	@Test
	@DisplayName("알림이 없을 경우 빈 리스트를 반환한다.")
	void findAllNotifications_returnEmptyCursorPage_whenNoNotification() {
		// given
		UUID noNotificationUser = UUID.fromString("00000000-0000-0000-0000-000000000003");
		insertUser(noNotificationUser, "receiver3");

		NotificationSearchRequest request = new NotificationSearchRequest(
			null, null,
			5,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		// when
		NotificationCursorPage result = notificationRepository.findAllNotifications(request, noNotificationUser);

		// then
		assertTrue(result.notificationList().isEmpty());
		assertFalse(result.hasNext());
		assertEquals(0L, result.totalCount());
	}

	@Test
	@DisplayName("cursor가 Instant 형식이 아니면 예외가 발생한다.")
	void findAllNotifications_throwException_whenInvalidCursorFormat() {
		// given
		NotificationSearchRequest request = new NotificationSearchRequest(
			"invalid_cursor_format",
			notification1,
			5,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		// when, then
		assertThrows(NotificationException.class,
			() -> notificationRepository.findAllNotifications(request, receiver1));
	}

	private void insertFixtures() {
		insertUser(receiver1, "receiver1");
		insertUser(receiver2, "receiver2");

		insertNotification(
			notification1,
			receiver1,
			"최신 알림",
			"최신 알림 내용",
			NotificationType.DM,
			NotificationLevel.INFO,
			null,
			createdAt1
		);
		insertNotification(
			notification2,
			receiver1,
			"동일 시간 알림 id 큼",
			"동일 시간 알림 내용",
			NotificationType.SUBSCRIBE,
			NotificationLevel.INFO,
			null,
			createdAt2
		);
		insertNotification(
			notification3,
			receiver1,
			"동일 시간 알림 id 작음",
			"동일 시간 알림 내용",
			NotificationType.CONTENT_ADDED,
			NotificationLevel.INFO,
			null,
			createdAt2
		);
		insertNotification(
			notification4,
			receiver1,
			"오래된 알림",
			"오래된 알림 내용",
			NotificationType.FOLLOW,
			NotificationLevel.INFO,
			null,
			createdAt3
		);
		insertNotification(
			readNotification,
			receiver1,
			"읽은 알림",
			"조회되면 안 되는 알림",
			NotificationType.ROLE_CHANGE,
			NotificationLevel.INFO,
			readAt,
			Instant.parse("2026-06-24T04:00:00Z")
		);
		insertNotification(
			otherUserNotification,
			receiver2,
			"다른 사용자 알림",
			"조회되면 안 되는 알림",
			NotificationType.DM,
			NotificationLevel.INFO,
			null,
			Instant.parse("2026-06-24T05:00:00Z")
		);
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
		String title,
		String content,
		NotificationType type,
		NotificationLevel level,
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
			title,
			content,
			type.toString(),
			level.toString(),
			readAt == null ? null : Timestamp.from(readAt),
			Timestamp.from(createdAt)
		);
	}
}