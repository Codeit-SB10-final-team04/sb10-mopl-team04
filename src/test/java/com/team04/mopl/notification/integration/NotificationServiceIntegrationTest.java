package com.team04.mopl.notification.integration;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.notification.dto.request.NotificationPageRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationSortBy;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.exception.NotificationErrorCode;
import com.team04.mopl.notification.exception.NotificationException;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.notification.service.NotificationService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Transactional
class NotificationServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("알림 생성 시 DTO를 반환하고 실제 DB에 저장한다.")
	void saveNotificationList_returnsDtoAndSavesToDatabase() {
		// given
		User receiver = createUser("알림 수신자");
		UUID sourceEventId = UUID.randomUUID();

		// when
		List<NotificationDto> result =
			notificationService.saveNotificationList(
				Set.of(receiver.getId()),
				sourceEventId,
				"새 구독 알림",
				"다른 사용자가 플레이리스트를 구독했습니다.",
				NotificationType.SUBSCRIBE,
				NotificationLevel.INFO
			);

		// then
		assertThat(result).hasSize(1);

		NotificationDto dto = result.get(0);

		assertThat(dto.id()).isNotNull();
		assertThat(dto.receiverId()).isEqualTo(receiver.getId());
		assertThat(dto.title()).isEqualTo("새 구독 알림");
		assertThat(dto.level()).isEqualTo(NotificationLevel.INFO);

		entityManager.flush();
		entityManager.clear();

		Notification saved = notificationRepository
			.findById(dto.id())
			.orElseThrow();

		assertThat(saved.getReceiver().getId())
			.isEqualTo(receiver.getId());
		assertThat(saved.getSourceEventId())
			.isEqualTo(sourceEventId);
		assertThat(saved.getType())
			.isEqualTo(NotificationType.SUBSCRIBE);
	}

	@Test
	@DisplayName("같은 sourceEventId와 수신자로 재요청하면 알림을 중복 저장하지 않는다.")
	void saveNotificationList_skipsDuplicateSourceEvent() {
		// given
		User receiver = createUser("중복 알림 수신자");
		UUID sourceEventId = UUID.randomUUID();

		// when
		List<NotificationDto> first =
			notificationService.saveNotificationList(
				Set.of(receiver.getId()),
				sourceEventId,
				"중복 알림",
				"중복 알림 내용",
				NotificationType.SUBSCRIBE,
				NotificationLevel.INFO
			);

		List<NotificationDto> duplicate =
			notificationService.saveNotificationList(
				Set.of(receiver.getId()),
				sourceEventId,
				"중복 알림",
				"중복 알림 내용",
				NotificationType.SUBSCRIBE,
				NotificationLevel.INFO
			);

		// then
		assertThat(first).hasSize(1);
		assertThat(duplicate).isEmpty();

		entityManager.flush();
		entityManager.clear();

		long savedCount = notificationRepository.findAll().stream()
			.filter(notification ->
				sourceEventId.equals(notification.getSourceEventId())
					&& receiver.getId().equals(
					notification.getReceiver().getId()
				)
			)
			.count();

		assertThat(savedCount).isEqualTo(1L);
	}

	@Test
	@DisplayName("알림 목록 조회 시 현재 사용자의 알림만 반환한다.")
	void findAllNotifications_returnsOnlyCurrentUserData() {
		// given
		User receiver = createUser("목록 조회 수신자");
		User otherUser = createUser("다른 사용자");

		createNotification(receiver, "첫 번째 알림");
		createNotification(receiver, "두 번째 알림");
		createNotification(otherUser, "다른 사용자 알림");

		NotificationPageRequest request = new NotificationPageRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		// when
		CursorResponseNotificationDto result =
			notificationService.findAllNotifications(
				request,
				receiver.getId()
			);

		// then
		assertThat(result.data()).hasSize(2);
		assertThat(result.totalCount()).isEqualTo(2L);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.sortBy()).isEqualTo("createdAt");
		assertThat(result.sortDirection())
			.isEqualTo(SortDirection.DESCENDING);

		assertThat(result.data())
			.allSatisfy(notification ->
				assertThat(notification.receiverId())
					.isEqualTo(receiver.getId())
			);
	}

	@Test
	@DisplayName("알림 읽음 처리 시 readAt이 기록된다.")
	void readNotification_marksReadAt() {
		// given
		User receiver = createUser("읽음 처리 수신자");
		Notification notification =
			createNotification(receiver, "읽음 처리 대상");

		// when
		notificationService.readNotification(
			notification.getId(),
			receiver.getId()
		);

		// then
		entityManager.flush();
		entityManager.clear();

		Notification read = notificationRepository
			.findById(notification.getId())
			.orElseThrow();

		assertThat(read.getReadAt()).isNotNull();
	}

	@Test
	@DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다.")
	void readNotification_throwsNotFound_whenReceiverDoesNotMatch() {
		// given
		User receiver = createUser("실제 수신자");
		User otherUser = createUser("다른 사용자");
		Notification notification =
			createNotification(receiver, "접근할 수 없는 알림");

		// when, then
		assertThatThrownBy(() ->
			notificationService.readNotification(
				notification.getId(),
				otherUser.getId()
			)
		)
			.isInstanceOf(NotificationException.class)
			.satisfies(exception ->
				assertThat(
					((NotificationException)exception).getErrorCode()
				).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND)
			);
	}

	private User createUser(String name) {
		UUID userId = UUID.randomUUID();

		jdbcTemplate.update("""
				INSERT INTO users (
					id,
					name,
					email,
					email_type,
					role,
					is_locked,
					created_at,
					updated_at
				)
				VALUES (?, ?, ?, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			userId,
			name,
			userId + "@test.com"
		);

		return userRepository.findById(userId).orElseThrow();
	}

	private Notification createNotification(
		User receiver,
		String title
	) {
		return notificationRepository.saveAndFlush(
			Notification.builder()
				.receiver(receiver)
				.sourceEventId(UUID.randomUUID())
				.title(title)
				.content(title + " 내용")
				.type(NotificationType.SUBSCRIBE)
				.level(NotificationLevel.INFO)
				.build()
		);
	}
}