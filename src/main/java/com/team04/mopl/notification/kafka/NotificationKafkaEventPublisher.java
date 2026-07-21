package com.team04.mopl.notification.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.notification.kafka.exception.KafkaEventErrorCode;
import com.team04.mopl.notification.kafka.exception.KafkaEventException;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.event.PlaylistCreatedEvent;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;
import com.team04.mopl.user.event.UserRoleChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Spring Event를 받아서 Kafka topic으로 발행하는 Listener
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationKafkaEventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishPlaylistSubscribedEvent(PlaylistSubscribedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] Kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.PLAYLIST_SUBSCRIBED,
			PlaylistSubscribedEvent.class.getSimpleName(),
			event.eventId());

		// PlaylistSubscribedEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.PLAYLIST_SUBSCRIBED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishPlaylistContentAddedEvent(PlaylistContentAddedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] Kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.PLAYLIST_CONTENT_ADDED,
			PlaylistContentAddedEvent.class.getSimpleName(),
			event.eventId());

		// PlaylistContentAddEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.PLAYLIST_CONTENT_ADDED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishFollowCreatedEvent(FollowCreatedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] Kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.FOLLOW_CREATED,
			FollowCreatedEvent.class.getSimpleName(),
			event.eventId());

		// FollowCreatedEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.FOLLOW_CREATED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishPlaylistCreatedEvent(PlaylistCreatedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] Kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.PLAYLIST_CREATED,
			PlaylistCreatedEvent.class.getSimpleName(),
			event.eventId());

		// PlaylistCreatedEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.PLAYLIST_CREATED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishUserRoleChangedEvent(UserRoleChangedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] Kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.USER_ROLE_CHANGED,
			UserRoleChangedEvent.class.getSimpleName(),
			event.eventId());

		// UserRoleUpdatedEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.USER_ROLE_CHANGED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishDirectMessageCreatedEvent(DirectMessageCreatedEvent event) {
		log.info("[NOTIFICATION_KAFKA_PUBLISH_START] kafka 이벤트 발행 시작: topic={}, eventType={}, eventId={}",
			NotificationKafkaTopics.DIRECT_MESSAGE_CREATED,
			DirectMessageCreatedEvent.class.getSimpleName(),
			event.eventId()
		);

		// DirectMessageCreatedEvent를 String으로 변환
		sendEvent(NotificationKafkaTopics.DIRECT_MESSAGE_CREATED, event);
	}

	private void sendEvent(String topic, Object event) {
		try {
			// event를 String으로 변환
			String payload = objectMapper.writeValueAsString(event);

			kafkaTemplate.send(topic, payload)
				.whenComplete((result, e) -> {
						if (e != null) {
							log.error("[NOTIFICATION_KAFKA_PUBLISH_FAILED] Kafka 이벤트 발행 실패: topic={}, eventType={}",
								topic, event.getClass().getSimpleName(), e);
						} else {
							log.debug("[NOTIFICATION_KAFKA_PUBLISH_SUCCESS] Kafka 이벤트 발행 성공: topic={}, eventType={}",
								topic, event.getClass().getSimpleName());
						}
					}
				);
		} catch (JsonProcessingException e) {
			log.error("[NOTIFICATION_KAFKA_SERIALIZATION_FAILED] Kafka 이벤트 직렬화 실패: topic={}, eventType={}",
				topic, event.getClass().getSimpleName(), e);
			throw new KafkaEventException(KafkaEventErrorCode.KAFKA_EVENT_SERIALIZATION_FAILED, e)
				.addDetail("topic", topic)
				.addDetail("event", event.getClass().getSimpleName());
		}
	}
}
