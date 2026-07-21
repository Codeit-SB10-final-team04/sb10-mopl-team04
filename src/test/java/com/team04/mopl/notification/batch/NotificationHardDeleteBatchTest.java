package com.team04.mopl.notification.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.support.IntegrationTestBase;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:notification-hard-delete-batch-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-batch-test.sql",
	"spring.batch.jdbc.initialize-schema=always",
	"spring.batch.job.enabled=false"
})
public class NotificationHardDeleteBatchTest extends IntegrationTestBase {

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job notificationHardDeleteJob;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("알림 물리 삭제 배치는 기준일 이전에 읽음 처리된 알림만 삭제한다.")
	void notificationHardDeleteJob_deleteExpiredReadAtNotifications() throws Exception {
		// given
		LocalDate deleteDate = LocalDate.of(2026, 6, 1);

		UUID userId = UUID.randomUUID();
		UUID deletedNotificationId1 = UUID.randomUUID();
		UUID deletedNotificationId2 = UUID.randomUUID();
		UUID cutoffBoundaryNotificationId = UUID.randomUUID();
		UUID retentionNotificationId = UUID.randomUUID();
		UUID activeNotificationId = UUID.randomUUID();

		insertUser(userId, "test");
		insertNotification(deletedNotificationId1, userId, "삭제1", "삭제1!!", Instant.parse("2026-04-01T00:00:00Z"));
		insertNotification(deletedNotificationId2, userId, "삭제2", "삭제2!!", Instant.parse("2026-04-01T00:00:00Z"));
		insertNotification(cutoffBoundaryNotificationId, userId, "경계", "경계!!", Instant.parse("2026-05-31T15:00:00Z"));
		insertNotification(retentionNotificationId, userId, "보관", "보관!!", Instant.parse("2026-06-24T00:00:00Z"));
		insertNotification(activeNotificationId, userId, "활성", "활성!!", null);

		JobParameters jobParameters = new JobParametersBuilder()
			.addLocalDate("deleteDate", deleteDate)
			.addLong("runId", System.currentTimeMillis())
			.toJobParameters();

		// when
		JobExecution jobExecution = jobLauncher.run(notificationHardDeleteJob, jobParameters);

		// then
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
		assertFalse(notificationRepository.findById(deletedNotificationId1).isPresent());
		assertFalse(notificationRepository.findById(deletedNotificationId2).isPresent());
		assertTrue(notificationRepository.findById(cutoffBoundaryNotificationId).isPresent());
		assertTrue(notificationRepository.findById(retentionNotificationId).isPresent());
		assertTrue(notificationRepository.findById(activeNotificationId).isPresent());
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
		Instant readAt
	) {
		jdbcTemplate.update("""
				INSERT INTO notifications (
				    id, receiver_id, title, content, type, level, read_at, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
				""",
			notificationId,
			receiverId,
			title,
			content,
			NotificationType.DM.toString(),
			NotificationLevel.INFO.toString(),
			readAt == null ? null : Timestamp.from(readAt)
		);
	}
}
