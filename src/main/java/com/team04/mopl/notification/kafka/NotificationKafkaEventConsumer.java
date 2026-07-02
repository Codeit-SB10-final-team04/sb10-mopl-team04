package com.team04.mopl.notification.kafka;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.kafka.exception.KafkaEventErrorCode;
import com.team04.mopl.notification.kafka.exception.KafkaEventException;
import com.team04.mopl.notification.service.NotificationService;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.event.PlaylistCreatedEvent;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Kafka topic을 구독해서 알림 저장/전송하는 listener
// TODO: SSE를 이용해 서버에서 실시간 알림 전송 구현 예정
// TODO: 지금은 DB에 저장까지만
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationKafkaEventConsumer {

	private final PlaylistSubscriptionRepository playlistSubscriptionRepository;
	private final FollowRepository followRepository;

	private final NotificationService notificationService;

	private final ObjectMapper objectMapper;

	// 특정 사용자가 플레이리스트를 구독 완료 후 플레이리스트 소유주에게 알림을 보내는 listener
	@KafkaListener(topics = NotificationKafkaTopics.PLAYLIST_SUBSCRIBED)
	public void consumePlaylistSubscribedEvent(String kafkaEvent) {
		PlaylistSubscribedEvent event = deserialize(kafkaEvent, PlaylistSubscribedEvent.class);

		// title
		String title = "새 플레이리스트 구독 알림";

		// content
		String content = String.format("[%s] 님이 [%s] 플레이리스트를 구독했습니다.",
			event.subscriberName(),
			event.playlistTitle()
		);

		// 알림 저장 및 TODO: SSE 전송
		sendNotification(
			Set.of(event.playlistOwnerId()),
			title,
			content,
			NotificationType.SUBSCRIBE,
			NotificationLevel.INFO
		);
	}

	// 구독 중인 플레이리스트에 콘텐츠가 추가되면 해당 플레이리스트 구독자에게 알림을 보내는 listener
	@KafkaListener(topics = NotificationKafkaTopics.PLAYLIST_CONTENT_ADDED)
	public void consumePlaylistContentAddedEvent(String kafkaEvent) {
		PlaylistContentAddedEvent event = deserialize(kafkaEvent, PlaylistContentAddedEvent.class);

		// title
		String title = "새 콘텐츠 추가 알림";

		// content
		String content = String.format("[%s] 플레이리스트에 [%s] 이(가) 추가되었습니다.",
			event.playlistTitle(),
			event.contentTitle()
		);

		// 보낼 사용자 찾기
		Set<UUID> subscriberIds = playlistSubscriptionRepository
			.findSubscriberIdsByPlaylistId(event.playlistId());

		// 알림 저장 및 TODO: SSE 전송
		sendNotification(
			subscriberIds,
			title,
			content,
			NotificationType.CONTENT_ADD,
			NotificationLevel.INFO
		);
	}

	// 특정 사용자를 팔로우 하면 해당 팔로우를 당한 사용자에게 알림을 보내는 listener
	@KafkaListener(topics = NotificationKafkaTopics.FOLLOW_CREATED)
	public void consumeFollowCreatedEvent(String kafkaEvent) {
		FollowCreatedEvent event = deserialize(kafkaEvent, FollowCreatedEvent.class);

		// title
		String title = "새 팔로우 알림";

		// content
		String content = String.format("[%s] 님이 팔로우했습니다.", event.followerName());

		// 알림 저장 및 TODO: SSE 전송
		sendNotification(
			Set.of(event.followeeId()),
			title,
			content,
			NotificationType.FOLLOW,
			NotificationLevel.INFO
		);
	}

	// 특정 사용자가 플레이리스트를 생성하면 해당 사용자의 팔로워에게 알림을 보내는 listener
	@KafkaListener(topics = NotificationKafkaTopics.PLAYLIST_CREATED)
	public void consumePlaylistCreatedEvent(String kafkaEvent) {
		PlaylistCreatedEvent event = deserialize(kafkaEvent, PlaylistCreatedEvent.class);

		// title
		String title = "새 활동 알림";

		// content
		String content = String.format("[%s] 님이 [%s] 플레이리스트를 생성하셨습니다.",
			event.playlistOwnerName(),
			event.playlistTitle()
		);

		// 해당 사용자를 팔로우한 사용자 id 목록 조회
		Set<UUID> followerIds = followRepository
			.findFollowerIdsByFolloweeId(event.playlistOwnerId());

		sendNotification(
			followerIds,
			title,
			content,
			NotificationType.FOLLOWING_ACTIVITY,
			NotificationLevel.INFO
		);
	}

	// KafkaEvent 역직렬화
	private <T> T deserialize(String kafkaEvent, Class<T> eventClass) {
		try {
			return objectMapper.readValue(kafkaEvent, eventClass);
		} catch (JsonProcessingException e) {
			log.error("[EVENT_DESERIALIZATION_FAILED] 이벤트 역직렬화 실패", e);
			throw new KafkaEventException(KafkaEventErrorCode.KAFKA_EVENT_DESERIALIZATION_FAILED)
				.addDetail("event", eventClass.getSimpleName());
		}
	}

	private void sendNotification(
		Set<UUID> receiverIds,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		List<NotificationDto> notificationDtoList = notificationService.saveNotificationList(
			receiverIds,
			title,
			content,
			type,
			level
		);

		// TODO: SSE로 알림 전송
	}
}
