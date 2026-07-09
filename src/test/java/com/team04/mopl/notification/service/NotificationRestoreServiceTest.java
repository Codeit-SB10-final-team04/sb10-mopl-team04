package com.team04.mopl.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.mapper.NotificationMapper;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationRestoreServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationMapper notificationMapper;

	@InjectMocks
	private NotificationRestoreService notificationRestoreService;

	@Test
	@DisplayName("lastEventId 기준 알림이 없으면 빈 리스트를 반환한다.")
	void findUnreadNotificationsAfter_returnEmptyList_whenLastNotificationNotFound() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		when(notificationRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.thenReturn(Optional.empty());

		// when
		List<NotificationDto> result =
			notificationRestoreService.findUnreadNotificationsAfter(receiverId, lastEventId);

		// then
		assertTrue(result.isEmpty());

		verify(notificationRepository).findByIdAndReceiverId(lastEventId, receiverId);
		verify(notificationRepository, never()).findUnreadNotificationsAfter(
			any(UUID.class), any(UUID.class), any(), any());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("lastEventId 이후 미읽음 알림을 조회해 NotificationDto 리스트로 반환한다.")
	void findUnreadNotificationsAfter_returnNotificationDtos_whenUnreadNotificationExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		User receiver = createUser(receiverId);
		Notification lastNotification = createNotification(
			lastEventId,
			receiver,
			Instant.parse("2026-06-24T02:00:00Z")
		);

		Notification notification1 = createNotification(
			UUID.randomUUID(),
			receiver,
			Instant.parse("2026-06-24T03:00:00Z")
		);
		Notification notification2 = createNotification(
			UUID.randomUUID(),
			receiver,
			Instant.parse("2026-06-24T04:00:00Z")
		);

		NotificationDto notificationDto1 = new NotificationDto(
			notification1.getId(),
			notification1.getCreatedAt(),
			notification1.getReceiver().getId(),
			notification1.getTitle(),
			notification1.getContent(),
			notification1.getLevel()
		);
		NotificationDto notificationDto2 = new NotificationDto(
			notification2.getId(),
			notification2.getCreatedAt(),
			notification2.getReceiver().getId(),
			notification2.getTitle(),
			notification2.getContent(),
			notification2.getLevel()
		);

		when(notificationRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.thenReturn(Optional.of(lastNotification));
		when(notificationRepository.findUnreadNotificationsAfter(
			receiverId, lastEventId, lastNotification.getCreatedAt(), PageRequest.of(0, 500)
		)).thenReturn(List.of(notification1, notification2));
		when(notificationMapper.toDto(notification1)).thenReturn(notificationDto1);
		when(notificationMapper.toDto(notification2)).thenReturn(notificationDto2);

		// when
		List<NotificationDto> result = notificationRestoreService.findUnreadNotificationsAfter(
			receiverId,
			lastEventId
		);

		// then
		assertEquals(List.of(notificationDto1, notificationDto2), result);

		verify(notificationRepository).findByIdAndReceiverId(lastEventId, receiverId);
		verify(notificationRepository).findUnreadNotificationsAfter(
			receiverId, lastEventId, lastNotification.getCreatedAt(), PageRequest.of(0, 500)
		);
		verify(notificationMapper).toDto(notification1);
		verify(notificationMapper).toDto(notification2);
	}

	private User createUser(UUID userId) {
		User user = User.builder()
			.name("테스트 사용자")
			.email("test@test.com")
			.profileImageUrl("https://example.com/profile.png")
			.build();
		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}

	private Notification createNotification(UUID notificationId, User receiver, Instant createdAt) {
		Notification notification = Notification.builder()
			.receiver(receiver)
			.title("테스트 알림 제목")
			.content("테스트 알림 내용")
			.type(NotificationType.DM)
			.level(NotificationLevel.INFO)
			.build();
		ReflectionTestUtils.setField(notification, "id", notificationId);
		ReflectionTestUtils.setField(notification, "createdAt", createdAt);

		return notification;
	}
}