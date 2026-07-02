package com.team04.mopl.notification.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.notification.kafka.exception.KafkaEventErrorCode;
import com.team04.mopl.notification.kafka.exception.KafkaEventException;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;

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
		// PlaylistSubscribedEvent를 String으로 변환 후 payload 변수에 할당
		sendEvent(NotificationKafkaTopics.PLAYLIST_SUBSCRIBED, event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishPlaylistContentAddEvent(PlaylistContentAddedEvent event) {
		// PlaylistContentAddEvent를 String으로 변환 후 payload 변수에 할당
		sendEvent(NotificationKafkaTopics.PLAYLIST_CONTENT_ADD, event);
	}

	private void sendEvent(String topic, Object event) {
		try {
			// event를 String으로 변환 후 payload 변수에 할당
			String payload = objectMapper.writeValueAsString(event);

			kafkaTemplate.send(topic, payload)
				.whenComplete((result, e) -> {
						if (e != null) {
							log.error("[KAFKA_EVENT_PUBLISH_FAILED] Kafka 이벤트 발행 실패: topic={}, event={}",
								topic, event.getClass().getSimpleName(), e);
						} else {
							log.debug("[KAFKA_EVENT_PUBLISH_SUCCESS] Kafka 이벤트 발행 성공: topic={}, event={}",
								topic, event.getClass().getSimpleName());
						}
					}
				);
		} catch (JsonProcessingException e) {
			log.error("[EVENT_SERIALIZATION_FAILED] 이벤트 직렬화 실패", e);
			throw new KafkaEventException(KafkaEventErrorCode.KAFKA_EVENT_SERIALIZATION_FAILED)
				.addDetail("topic", topic)
				.addDetail("event", event.getClass().getSimpleName());
		}
	}
}
