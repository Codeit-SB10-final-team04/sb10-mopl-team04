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

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.notification.dto.request.NotificationSearchRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;
import com.team04.mopl.notification.dto.response.NotificationCursorPageDto;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationSortBy;
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
		List<NotificationDto> result = notificationService.saveNotificationList(
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
			() -> notificationService.saveNotificationList(
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
		List<NotificationDto> result = notificationService.saveNotificationList(
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
			() -> notificationService.saveNotificationList(
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
			() -> notificationService.saveNotificationList(
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
			() -> notificationService.saveNotificationList(
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

	@Test
	@DisplayName("정렬 조건이 createdAt인 알림 목록 조회에 성공하면 알림 커서 페이지네이션 응답 DTO를 반환한다.")
	void findAllNotifications_returnCursorResponse_whenSortByCreatedAt() {
		// given
		UUID currentUserId = UUID.randomUUID();
		Instant createdAt1 = Instant.parse("2026-06-24T01:00:00Z");
		Instant createdAt2 = Instant.parse("2026-06-24T00:00:00Z");

		User receiver = createUser(currentUserId);
		Notification notification1 = createNotification(
			receiver,
			"테스트 제목1",
			"테스트 내용1",
			NotificationType.DM,
			NotificationLevel.INFO
		);
		Notification notification2 = createNotification(
			receiver,
			"테스트 제목2",
			"테스트 내용2",
			NotificationType.SUBSCRIBE,
			NotificationLevel.INFO
		);
		ReflectionTestUtils.setField(notification1, "createdAt", createdAt1);
		ReflectionTestUtils.setField(notification2, "createdAt", createdAt2);

		NotificationSearchRequest request = new NotificationSearchRequest(
			null, null,
			2,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
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

		NotificationCursorPageDto notificationCursorPageDto = new NotificationCursorPageDto(
			List.of(notification1, notification2),
			true,
			3L
		);

		CursorResponseNotificationDto cursorResponseNotificationDto = new CursorResponseNotificationDto(
			List.of(notificationDto1, notificationDto2),
			notificationDto2.createdAt().toString(),
			notificationDto2.id(),
			true,
			3L,
			NotificationSortBy.createdAt.toString(),
			SortDirection.DESCENDING
		);

		when(notificationRepository.findAllNotifications(request, currentUserId))
			.thenReturn(notificationCursorPageDto);
		when(notificationMapper.toDto(any(Notification.class)))
			.thenReturn(notificationDto1, notificationDto2);

		// when
		CursorResponseNotificationDto result = notificationService.findAllNotifications(
			request,
			currentUserId
		);

		// then
		assertEquals(cursorResponseNotificationDto, result);
		assertEquals(List.of(notificationDto1, notificationDto2), result.data());
		assertEquals(notificationDto2.createdAt().toString(), result.nextCursor());
		assertEquals(notificationDto2.id(), result.nextIdAfter());
		assertTrue(result.hasNext());
		assertEquals(3L, result.totalCount());
		assertEquals(NotificationSortBy.createdAt.toString(), result.sortBy());
		assertEquals(SortDirection.DESCENDING, result.sortDirection());

		verify(notificationRepository).findAllNotifications(request, currentUserId);

		ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

		verify(notificationMapper, times(2))
			.toDto(notificationCaptor.capture());
		List<Notification> capturedNotificationList = notificationCaptor.getAllValues();

		assertEquals(List.of(notification1, notification2), capturedNotificationList);
	}

	@Test
	@DisplayName("알림 목록 조회 시 데이터가 없다면 빈 리스트를 반환한다.")
	void findAllNotifications_returnEmptyCursorResponse_whenNoData() {
		// given
		UUID currentUserId = UUID.randomUUID();

		NotificationSearchRequest request = new NotificationSearchRequest(
			null, null,
			2,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		NotificationCursorPageDto notificationCursorPageDto = new NotificationCursorPageDto(
			List.of(),
			false,
			0L
		);

		when(notificationRepository.findAllNotifications(request, currentUserId))
			.thenReturn(notificationCursorPageDto);

		// when
		CursorResponseNotificationDto result = notificationService.findAllNotifications(
			request,
			currentUserId
		);

		// then
		assertTrue(result.data().isEmpty());
		assertFalse(result.hasNext());
		assertEquals(0L, result.totalCount());
		assertEquals(NotificationSortBy.createdAt.toString(), result.sortBy());
		assertEquals(SortDirection.DESCENDING, result.sortDirection());

		verify(notificationRepository).findAllNotifications(request, currentUserId);
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