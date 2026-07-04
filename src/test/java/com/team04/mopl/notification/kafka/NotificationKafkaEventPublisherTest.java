package com.team04.mopl.notification.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.notification.kafka.exception.KafkaEventException;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.event.PlaylistCreatedEvent;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.event.UserRoleChangedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaEventPublisherTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private NotificationKafkaEventPublisher notificationKafkaEventPublisher;

	@Test
	@DisplayName("플레이리스트 구독 이벤트를 받으면 구독 topic으로 Kafka 이벤트를 발행한다.")
	public void publishPlaylistSubscribedEvent_sendKafkaEvent_whenValidEvent() throws Exception {
		// given
		PlaylistSubscribedEvent event = PlaylistSubscribedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"구독자 이름"
		);
		String payload = "{\"event\":\"playlistSubscribed\"}";

		when(objectMapper.writeValueAsString(event))
			.thenReturn(payload);
		stubKafkaSend(NotificationKafkaTopics.PLAYLIST_SUBSCRIBED, payload);

		// when
		notificationKafkaEventPublisher.publishPlaylistSubscribedEvent(event);

		// then
		verify(kafkaTemplate).send(NotificationKafkaTopics.PLAYLIST_SUBSCRIBED, payload);
	}

	@Test
	@DisplayName("플레이리스트 콘텐츠 추가 이벤트를 받으면 콘텐츠 추가 topic으로 Kafka 이벤트를 발행한다.")
	public void publishPlaylistContentAddedEvent_sendKafkaEvent_whenValidEvent() throws Exception {
		// given
		PlaylistContentAddedEvent event = PlaylistContentAddedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			UUID.randomUUID(),
			UUID.randomUUID(),
			"콘텐츠 제목"
		);
		String payload = "{\"event\":\"playlistContentAdded\"}";

		when(objectMapper.writeValueAsString(event))
			.thenReturn(payload);
		stubKafkaSend(NotificationKafkaTopics.PLAYLIST_CONTENT_ADDED, payload);

		// when
		notificationKafkaEventPublisher.publishPlaylistContentAddedEvent(event);

		// then
		verify(kafkaTemplate).send(NotificationKafkaTopics.PLAYLIST_CONTENT_ADDED, payload);
	}

	@Test
	@DisplayName("팔로우 생성 이벤트를 받으면 팔로우 topic으로 Kafka 이벤트를 발행한다.")
	void publishFollowCreatedEvent_sendKafkaEvent_whenValidEvent() throws Exception {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로우 대상 이름",
			UUID.randomUUID(),
			"팔로워 이름"
		);
		String payload = "{\"event\":\"followCreated\"}";

		when(objectMapper.writeValueAsString(event))
			.thenReturn(payload);
		stubKafkaSend(NotificationKafkaTopics.FOLLOW_CREATED, payload);

		// when
		notificationKafkaEventPublisher.publishFollowCreatedEvent(event);

		// then
		verify(kafkaTemplate).send(NotificationKafkaTopics.FOLLOW_CREATED, payload);
	}

	@Test
	@DisplayName("플레이리스트 생성 이벤트를 받으면 플레이리스트 생성 topic으로 Kafka 이벤트를 발행한다.")
	void publishPlaylistCreatedEvent_sendKafkaEvent_whenValidEvent() throws Exception {
		// given
		PlaylistCreatedEvent event = PlaylistCreatedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			UUID.randomUUID(),
			"플레이리스트 소유자 이름"
		);
		String payload = "{\"event\":\"playlistCreated\"}";

		when(objectMapper.writeValueAsString(event))
			.thenReturn(payload);
		stubKafkaSend(NotificationKafkaTopics.PLAYLIST_CREATED, payload);

		// when
		notificationKafkaEventPublisher.publishPlaylistCreatedEvent(event);

		// then
		verify(kafkaTemplate).send(NotificationKafkaTopics.PLAYLIST_CREATED, payload);
	}

	@Test
	@DisplayName("사용자 권한 변경 이벤트를 받으면 사용자 권한 변경 topic으로 Kafka 이벤트를 발행한다.")
	void publishUserRoleChangedEvent_sendKafkaEvent_whenValidEvent() throws Exception {
		// given
		UserRoleChangedEvent event = UserRoleChangedEvent.of(
			UUID.randomUUID(),
			UserRole.USER,
			UserRole.ADMIN
		);
		String payload = "{\"event\":\"userRoleUpdated\"}";

		when(objectMapper.writeValueAsString(event))
			.thenReturn(payload);
		stubKafkaSend(NotificationKafkaTopics.USER_ROLE_CHANGED, payload);

		// when
		notificationKafkaEventPublisher.publishUserRoleChangedEvent(event);

		// then
		verify(kafkaTemplate).send(NotificationKafkaTopics.USER_ROLE_CHANGED, payload);
	}

	@Test
	@DisplayName("Kafka 이벤트 직렬화에 실패하면 KafkaEventException이 발생한다.")
	void publishKafkaEvent_throwException_whenSerializationFailed() throws Exception {
		// given
		PlaylistCreatedEvent event = PlaylistCreatedEvent.of(
			UUID.randomUUID(),
			"플레이리스트 제목",
			UUID.randomUUID(),
			"플레이리스트 소유자 이름"
		);
		JsonProcessingException exception = new JsonProcessingException("직렬화 실패") {
		};

		when(objectMapper.writeValueAsString(event))
			.thenThrow(exception);

		// when
		assertThrows(KafkaEventException.class,
			() -> notificationKafkaEventPublisher.publishPlaylistCreatedEvent(event));

		// then
		verify(kafkaTemplate, never()).send(anyString(), anyString());
	}

	private void stubKafkaSend(String topic, String payload) {
		CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
		when(kafkaTemplate.send(topic, payload))
			.thenReturn(future);
	}
}