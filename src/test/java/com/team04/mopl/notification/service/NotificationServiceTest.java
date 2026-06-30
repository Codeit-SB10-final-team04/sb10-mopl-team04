package com.team04.mopl.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.exception.NotificationException;
import com.team04.mopl.notification.mapper.NotificationMapper;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationMapper notificationMapper;

	@InjectMocks
	private NotificationService notificationService;

	@Test
	@DisplayName("여러 수신자 알림 생성 요청에 성공하면 수신자 수만큼 알림을 저장한다.")
	void createNotificationList_saveNotificationList_whenValidReqiest() {
		// given
		UUID receiverId1 = UUID.randomUUID();
		UUID receiverId2 = UUID.randomUUID();

		User receiver1 = createUser(receiverId1);
		User receiver2 = createUser(receiverId2);

		NotificationDto notificationDto1 = new NotificationDto(
			UUID.randomUUID(),
			Instant.now(),
			receiverId1,
			"알림 제목",
			"알림 내용",
			NotificationLevel.INFO
		);

		NotificationDto notificationDto2 = new NotificationDto(
			UUID.randomUUID(),
			Instant.now(),
			receiverId2,
			"알림 제목",
			"알림 내용",
			NotificationLevel.INFO
		);

		when(userRepository.findAllByIdInAndLockedFalse(Set.of(receiverId1, receiverId2)))
			.thenReturn(List.of(receiver1, receiver2));
		when(notificationRepository.saveAll(anyList()))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(notificationMapper.toDto(any(Notification.class)))
			.thenReturn(notificationDto1, notificationDto2);

		// when
		List<NotificationDto> result = notificationService.createNotificationList(
			Set.of(receiverId1, receiverId2),
			"알림 제목",
			"알림 내용",
			NotificationType.ROLE_CHANGE,
			NotificationLevel.INFO
		);

		// then
		assertEquals(2, result.size());
		assertTrue(result.contains(notificationDto1));
		assertTrue(result.contains(notificationDto2));

		ArgumentCaptor<List<Notification>> notificationListCaptor =
			ArgumentCaptor.forClass(List.class);
		verify(notificationRepository).saveAll(notificationListCaptor.capture());

		List<Notification> notificationList = notificationListCaptor.getValue();
		assertEquals(2, notificationList.size());
		assertTrue(notificationList.stream()
			.allMatch(notification -> notification.getTitle().equals("알림 제목")));
		assertTrue(notificationList.stream()
			.allMatch(notification -> notification.getContent().equals("알림 내용")));
		assertTrue(notificationList.stream()
			.allMatch(notification -> notification.getType() == NotificationType.ROLE_CHANGE));
		assertTrue(notificationList.stream()
			.allMatch(notification -> notification.getLevel() == NotificationLevel.INFO));

		verify(userRepository).findAllByIdInAndLockedFalse(Set.of(receiverId1, receiverId2));
		verify(notificationRepository).saveAll(anyList());
		verify(notificationMapper, times(2)).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("수신자 목록이 null이면 알림 리스트 저장 시 예외가 발생한다.")
	void createNotificationList_throwException_whenReceiverIdsIsNull() {
		// when
		assertThrows(NotificationException.class,
			() -> notificationService.createNotificationList(
				null,
				"알림 제목",
				"알림 내용",
				NotificationType.ROLE_CHANGE,
				NotificationLevel.INFO
			));

		// then
		verify(userRepository, never()).findAllByIdInAndLockedFalse(anySet());
		verify(notificationRepository, never()).saveAll(anyList());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("수신자 목록이 비어있으면 알림 리스트를 저장하지 않고 빈 목록을 반환한다.")
	void createNotificationList_throwException_whenNotificationIdsIsBlank() {
		// when
		List<NotificationDto> result = notificationService.createNotificationList(
			Set.of(),
			"알림 제목",
			"알림 내용",
			NotificationType.ROLE_CHANGE,
			NotificationLevel.INFO
		);

		// then
		assertTrue(result.isEmpty());

		verify(userRepository, never()).findAllByIdInAndLockedFalse(anySet());
		verify(notificationRepository, never()).saveAll(anyList());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("input 값이 null 또는 blank면 알림 리스트 저장 시 예외가 발생한다.")
	void createNotificationList_throwException_whenInputIsNullOrBlank() {
		// given
		UUID receiverId = UUID.randomUUID();

		// when
		assertThrows(NotificationException.class,
			() -> notificationService.createNotificationList(
				Set.of(receiverId),
				" ",
				"알림 내용",
				NotificationType.ROLE_CHANGE,
				NotificationLevel.INFO
			));

		// then
		verify(userRepository, never()).findAllByIdInAndLockedFalse(anySet());
		verify(notificationRepository, never()).saveAll(anyList());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("title 값의 길이가 50 글자를 초과하면 알림 리스트 저장 시 예외가 발생한다.")
	void createNotificationList_throwException_whenTitleLengthExceed() {
		// given
		UUID receiverId = UUID.randomUUID();

		// when
		assertThrows(NotificationException.class,
			() -> notificationService.createNotificationList(
				Set.of(receiverId),
				"테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목테스트제목",
				"알림 내용",
				NotificationType.ROLE_CHANGE,
				NotificationLevel.INFO
			));

		// then
		verify(userRepository, never()).findAllByIdInAndLockedFalse(anySet());
		verify(notificationRepository, never()).saveAll(anyList());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	@Test
	@DisplayName("존재하지 않는 수신인으로 알림 저장 시 예외가 발생한다.")
	void createNotificationList_throwException_whenUserNotFound() {
		// given
		UUID receiverId1 = UUID.randomUUID();
		UUID receiverId2 = UUID.randomUUID();

		User receiver1 = createUser(receiverId1);

		when(userRepository.findAllByIdInAndLockedFalse(Set.of(receiverId1, receiverId2)))
			.thenReturn(List.of(receiver1));

		// when
		assertThrows(NotificationException.class,
			() -> notificationService.createNotificationList(
				Set.of(receiverId1, receiverId2),
				"알림 제목",
				"알림 내용",
				NotificationType.ROLE_CHANGE,
				NotificationLevel.INFO
			));

		// then
		verify(userRepository).findAllByIdInAndLockedFalse(Set.of(receiverId1, receiverId2));
		verify(notificationRepository, never()).saveAll(anyList());
		verify(notificationMapper, never()).toDto(any(Notification.class));
	}

	private User createUser(UUID userId) {
		User user = User.builder()
			.name("테스트 사용자")
			.email("test@gmail.com")
			.profileImageUrl("https://example.com")
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}

	private Notification createNotification(
		User receiver,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		Notification notification = Notification.builder()
			.receiver(receiver)
			.title(title)
			.content(content)
			.type(type)
			.level(level)
			.build();
		ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
		return notification;
	}
}