package com.team04.mopl.notification.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.kafka.exception.KafkaEventException;
import com.team04.mopl.notification.metrics.NotificationMetrics;
import com.team04.mopl.notification.realtime.NotificationRealtimePublisher;
import com.team04.mopl.notification.service.NotificationService;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.event.PlaylistCreatedEvent;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.event.UserRoleChangedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaEventConsumerTest {

	@Mock
	private PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private NotificationService notificationService;

	@Mock
	private NotificationRealtimePublisher notificationRealtimePublisher;

	@Mock
	private NotificationMetrics notificationMetrics;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private NotificationKafkaEventConsumer notificationKafkaEventConsumer;

	@Test
	@DisplayName("플레이리스트 구독 이벤트를 소비하면 플레이리스트 소유자에게 알림을 저장하고 실시간 전송한다.")
	void consumePlaylistSubscribedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws
		Exception {
		// given
		String kafkaEvent = "{}";
		UUID playlistOwnerId = UUID.randomUUID();
		String content = "[구독자 이름] 님이 [플레이리스트 제목] 플레이리스트를 구독했습니다.";

		PlaylistSubscribedEvent event = PlaylistSubscribedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			playlistOwnerId,
			UUID.randomUUID(),
			"구독자 이름"
		);

		NotificationDto notificationDto = createNotificationDto(playlistOwnerId);

		when(objectMapper.readValue(kafkaEvent, PlaylistSubscribedEvent.class))
			.thenReturn(event);
		when(notificationService.saveNotificationList(
			Set.of(playlistOwnerId),
			event.eventId(),
			"새 플레이리스트 구독 알림",
			content,
			NotificationType.SUBSCRIBE,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto));

		// when
		notificationKafkaEventConsumer.consumePlaylistSubscribedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(playlistOwnerId),
			event.eventId(),
			"새 플레이리스트 구독 알림",
			content,
			NotificationType.SUBSCRIBE,
			NotificationLevel.INFO
		);
		verify(notificationRealtimePublisher).publish(notificationDto);
	}

	@Test
	@DisplayName("플레이리스트 콘텐츠 추가 이벤트를 소비하면 플레이리스트 구독자에게 알림을 저장하고 실시간 전송한다.")
	void consumePlaylistContentAddedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws
		Exception {
		// given
		String kafkaEvent = "{}";
		UUID playlistId = UUID.randomUUID();
		UUID subscriberId1 = UUID.randomUUID();
		UUID subscriberId2 = UUID.randomUUID();
		String content = "[플레이리스트 제목] 플레이리스트에 [콘텐츠 제목] 이(가) 추가되었습니다.";

		PlaylistContentAddedEvent event = PlaylistContentAddedEvent.of(
			playlistId,
			"플레이리스트 제목",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"콘텐츠 제목"
		);

		NotificationDto notificationDto1 = createNotificationDto(subscriberId1);
		NotificationDto notificationDto2 = createNotificationDto(subscriberId2);

		when(objectMapper.readValue(kafkaEvent, PlaylistContentAddedEvent.class))
			.thenReturn(event);
		when(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
			.thenReturn(Set.of(subscriberId1, subscriberId2));
		when(notificationService.saveNotificationList(
			Set.of(subscriberId1, subscriberId2),
			event.eventId(),
			"새 콘텐츠 추가 알림",
			content,
			NotificationType.CONTENT_ADD,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto1, notificationDto2));

		// when
		notificationKafkaEventConsumer.consumePlaylistContentAddedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(subscriberId1, subscriberId2),
			event.eventId(),
			"새 콘텐츠 추가 알림",
			content,
			NotificationType.CONTENT_ADD,
			NotificationLevel.INFO
		);
		verify(playlistSubscriptionRepository).findSubscriberIdsByPlaylistId(playlistId);
		verify(notificationRealtimePublisher).publish(notificationDto1);
		verify(notificationRealtimePublisher).publish(notificationDto2);
	}

	@Test
	@DisplayName("팔로우 생성 이벤트를 소비하면 팔로우를 당한 사용자에게 알림을 저장하고 실시간 전송한다.")
	void consumeFollowCreatedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws Exception {
		// given
		String kafkaEvent = "{}";
		UUID followeeId = UUID.randomUUID();
		String content = "[팔로워 이름] 님이 팔로우했습니다.";

		FollowCreatedEvent event = FollowCreatedEvent.of(
			followeeId,
			"팔로우 당한 사용자 이름",
			UUID.randomUUID(),
			"팔로워 이름"
		);

		NotificationDto notificationDto = createNotificationDto(followeeId);

		when(objectMapper.readValue(kafkaEvent, FollowCreatedEvent.class))
			.thenReturn(event);
		when(notificationService.saveNotificationList(
			Set.of(followeeId),
			event.eventId(),
			"새 팔로우 알림",
			content,
			NotificationType.FOLLOW,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto));

		// when
		notificationKafkaEventConsumer.consumeFollowCreatedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(followeeId),
			event.eventId(),
			"새 팔로우 알림",
			content,
			NotificationType.FOLLOW,
			NotificationLevel.INFO
		);
		verify(notificationRealtimePublisher).publish(notificationDto);
	}

	@Test
	@DisplayName("플레이리스트 생성 이벤트를 소비하면 해당 사용자의 구독자에게 알림을 저장하고 실시간 전송한다.")
	void consumePlaylistCreatedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws
		Exception {
		// given
		String kafkaEvent = "{}";
		UUID followeeId = UUID.randomUUID();
		UUID followerId1 = UUID.randomUUID();
		UUID followerId2 = UUID.randomUUID();
		String content = "[플레이리스트 소유자 이름] 님이 [플레이리스트 제목] 플레이리스트를 생성하셨습니다.";

		PlaylistCreatedEvent event = PlaylistCreatedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			followeeId,
			"플레이리스트 소유자 이름"
		);

		NotificationDto notificationDto1 = createNotificationDto(followerId1);
		NotificationDto notificationDto2 = createNotificationDto(followerId2);

		when(objectMapper.readValue(kafkaEvent, PlaylistCreatedEvent.class))
			.thenReturn(event);
		when(followRepository.findFollowerIdsByFolloweeId(followeeId))
			.thenReturn(Set.of(followerId1, followerId2));
		when(notificationService.saveNotificationList(
			Set.of(followerId1, followerId2),
			event.eventId(),
			"새 활동 알림",
			content,
			NotificationType.FOLLOWING_ACTIVITY,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto1, notificationDto2));

		// when
		notificationKafkaEventConsumer.consumePlaylistCreatedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(followerId1, followerId2),
			event.eventId(),
			"새 활동 알림",
			content,
			NotificationType.FOLLOWING_ACTIVITY,
			NotificationLevel.INFO
		);
		verify(followRepository).findFollowerIdsByFolloweeId(followeeId);
		verify(notificationRealtimePublisher).publish(notificationDto1);
		verify(notificationRealtimePublisher).publish(notificationDto2);
	}

	@Test
	@DisplayName("사용자 권한 변경 이벤트를 소비하면 해당 사용자에게 알림을 저장하고 실시간 전송한다.")
	void consumeUserRoleChangedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws
		Exception {
		// given
		String kafkaEvent = "{}";
		UUID userId = UUID.randomUUID();
		String content = "[일반 사용자] 권한에서 [관리자] 권한으로 변경되었습니다.";

		UserRoleChangedEvent event = UserRoleChangedEvent.of(
			userId,
			UserRole.USER,
			UserRole.ADMIN
		);

		NotificationDto notificationDto = createNotificationDto(userId);

		when(objectMapper.readValue(kafkaEvent, UserRoleChangedEvent.class))
			.thenReturn(event);
		when(notificationService.saveNotificationList(
			Set.of(userId),
			event.eventId(),
			"권한 변경 알림",
			content,
			NotificationType.ROLE_CHANGE,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto));

		// when
		notificationKafkaEventConsumer.consumeUserRoleChangedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(userId),
			event.eventId(),
			"권한 변경 알림",
			content,
			NotificationType.ROLE_CHANGE,
			NotificationLevel.INFO
		);
		verify(notificationRealtimePublisher).publish(notificationDto);
	}

	@Test
	@DisplayName("DM 생성 이벤트를 소비하면 해당 사용자에게 알림을 저장하고 실시간 전송한다.")
	void consumeDirectMessageCreatedEvent_saveNotificationAndPublishRealtimeNotification_whenValidRequest() throws
		Exception {
		// given
		String kafkaEvent = "{}";
		UUID senderId = UUID.randomUUID();
		UUID receiverId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();
		UserSummary senderSummary = new UserSummary(
			senderId,
			"송신자",
			"https://profile.sender"
		);
		UserSummary receiverSummary = new UserSummary(
			receiverId,
			"수신자",
			"https://profile.receiver"
		);
		String content = "[송신자] 님이 DM을 보냈습니다.";

		DirectMessageDto directMessageDto = new DirectMessageDto(
			directMessageId,
			UUID.randomUUID(),
			Instant.now(),
			senderSummary,
			receiverSummary,
			"안녕하세요."
		);

		DirectMessageCreatedEvent event = DirectMessageCreatedEvent.of(
			receiverId,
			directMessageId,
			directMessageDto
		);

		NotificationDto notificationDto = createNotificationDto(receiverId);

		when(objectMapper.readValue(kafkaEvent, DirectMessageCreatedEvent.class))
			.thenReturn(event);
		when(notificationService.saveNotificationList(
			Set.of(receiverId),
			event.eventId(),
			"새 DM 알림",
			content,
			NotificationType.DM,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto));

		// when
		notificationKafkaEventConsumer.consumeDirectMessageCreatedEvent(kafkaEvent);

		// then
		verify(notificationService).saveNotificationList(
			Set.of(receiverId),
			event.eventId(),
			"새 DM 알림",
			content,
			NotificationType.DM,
			NotificationLevel.INFO
		);
		verify(notificationRealtimePublisher).publish(notificationDto);
	}

	@Test
	@DisplayName("수신자 중 한 명의 실시간 전송에 실패해도 나머지 수신자에게 실시간 전송이 계속된다.")
	void consumeKafkaEvent_continuePublishRealtimeNotification_whenOneReceiverPublishRealtimeNotificationFailed()
		throws Exception {
		// given
		String kafkaEvent = "{}";
		UUID playlistId = UUID.randomUUID();
		UUID subscriberId1 = UUID.randomUUID();
		UUID subscriberId2 = UUID.randomUUID();
		String content = "[플레이리스트 제목] 플레이리스트에 [콘텐츠 제목] 이(가) 추가되었습니다.";

		PlaylistContentAddedEvent event = PlaylistContentAddedEvent.of(
			playlistId,
			"플레이리스트 제목",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"콘텐츠 제목"
		);

		NotificationDto notificationDto1 = createNotificationDto(subscriberId1);
		NotificationDto notificationDto2 = createNotificationDto(subscriberId2);

		when(objectMapper.readValue(kafkaEvent, PlaylistContentAddedEvent.class))
			.thenReturn(event);
		when(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
			.thenReturn(Set.of(subscriberId1, subscriberId2));
		when(notificationService.saveNotificationList(
			Set.of(subscriberId1, subscriberId2),
			event.eventId(),
			"새 콘텐츠 추가 알림",
			content,
			NotificationType.CONTENT_ADD,
			NotificationLevel.INFO
		)).thenReturn(List.of(notificationDto1, notificationDto2));

		doThrow(new RuntimeException("Realtime Notification Publish Failed"))
			.when(notificationRealtimePublisher)
			.publish(notificationDto1);

		// when, then
		assertDoesNotThrow(() -> notificationKafkaEventConsumer.consumePlaylistContentAddedEvent(kafkaEvent));

		verify(notificationService).saveNotificationList(
			Set.of(subscriberId1, subscriberId2),
			event.eventId(),
			"새 콘텐츠 추가 알림",
			content,
			NotificationType.CONTENT_ADD,
			NotificationLevel.INFO
		);
		verify(playlistSubscriptionRepository).findSubscriberIdsByPlaylistId(playlistId);
		verify(notificationRealtimePublisher).publish(notificationDto1);
		verify(notificationRealtimePublisher).publish(notificationDto2);
	}

	@Test
	@DisplayName("Kafka 이벤트 역직렬화에 실패하면 알림 저장과 실시간 전송을 하지 않는다.")
	void consumeKafkaEvent_doNotSaveAndPublish_whenDeserializationFailed() throws Exception {
		// given
		String kafkaEvent = "invalid-kafka-event";

		when(objectMapper.readValue(kafkaEvent, PlaylistSubscribedEvent.class))
			.thenThrow(new JsonProcessingException("Invalid Kafka Event") {
			});

		// when, then
		assertThrows(KafkaEventException.class,
			() -> notificationKafkaEventConsumer.consumePlaylistSubscribedEvent(kafkaEvent));

		verify(notificationService, never()).saveNotificationList(
			anySet(),
			any(UUID.class),
			anyString(),
			anyString(),
			any(NotificationType.class),
			any(NotificationLevel.class)
		);
		verify(notificationRealtimePublisher, never()).publish(any(NotificationDto.class));
	}

	@Test
	@DisplayName("일부 수신자의 알림이 중복이면 저장 건수와 중복 제외 수신자 수를 기록한다.")
	void consumeKafkaEvent_recordCreatedAndDuplicateSkipped_whenSomeNotificationAreDuplicate() throws Exception {
		// given
		String kafkaEvent = "{}";
		UUID playlistId = UUID.randomUUID();
		UUID subscriberId1 = UUID.randomUUID();
		UUID subscriberId2 = UUID.randomUUID();
		UUID subscriberId3 = UUID.randomUUID();
		Set<UUID> subscriberIds = Set.of(subscriberId1, subscriberId2, subscriberId3);

		PlaylistContentAddedEvent event = PlaylistContentAddedEvent.of(
			playlistId,
			"플레이리스트 제목",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"콘텐츠 제목"
		);

		NotificationDto notificationDto1 = createNotificationDto(subscriberId1);
		NotificationDto notificationDto2 = createNotificationDto(subscriberId2);

		when(objectMapper.readValue(kafkaEvent, PlaylistContentAddedEvent.class))
			.thenReturn(event);
		when(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
			.thenReturn(subscriberIds);
		when(notificationService.saveNotificationList(
			eq(subscriberIds),
			eq(event.eventId()),
			anyString(),
			anyString(),
			eq(NotificationType.CONTENT_ADD),
			eq(NotificationLevel.INFO)
		)).thenReturn(List.of(notificationDto1, notificationDto2));

		// when
		notificationKafkaEventConsumer.consumePlaylistContentAddedEvent(kafkaEvent);

		// then
		verify(notificationMetrics).recordCreated(NotificationType.CONTENT_ADD, 2L);
		verify(notificationMetrics).recordDuplicateSkipped(NotificationType.CONTENT_ADD, 1L);
	}

	@Test
	@DisplayName("알림 저장에 실패하면 저장 실패 메트릭을 기록하고 예외를 다시 발생시킨다.")
	void consumeKafkaEvent_recordStoreFailureAndReThrow_whenNotificationStoreFails() throws Exception {
		// given
		String kafkaEvent = "{}";
		UUID playlistOwnerId = UUID.randomUUID();

		PlaylistSubscribedEvent event = PlaylistSubscribedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			playlistOwnerId,
			UUID.randomUUID(),
			"구독자 이름"
		);

		RuntimeException storeFailure = new RuntimeException("Notification Store Failed");

		when(objectMapper.readValue(kafkaEvent, PlaylistSubscribedEvent.class))
			.thenReturn(event);
		when(notificationService.saveNotificationList(
			eq(Set.of(playlistOwnerId)),
			eq(event.eventId()),
			anyString(),
			anyString(),
			eq(NotificationType.SUBSCRIBE),
			eq(NotificationLevel.INFO)
		)).thenThrow(storeFailure);

		// when
		RuntimeException actualException = assertThrows(RuntimeException.class,
			() -> notificationKafkaEventConsumer.consumePlaylistSubscribedEvent(kafkaEvent));

		// then
		assertEquals(storeFailure, actualException);

		verify(notificationMetrics).recordStoreFailure(NotificationType.SUBSCRIBE);
		verify(notificationMetrics, never()).recordCreated(any(NotificationType.class), anyLong());
		verify(notificationRealtimePublisher, never()).publish(any(NotificationDto.class));
	}

	private NotificationDto createNotificationDto(UUID receiverId) {
		return new NotificationDto(
			UUID.randomUUID(),
			Instant.now(),
			receiverId,
			"알림 제목",
			"알림 내용",
			NotificationLevel.INFO
		);
	}
}