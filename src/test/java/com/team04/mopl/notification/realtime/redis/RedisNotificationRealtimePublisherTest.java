package com.team04.mopl.notification.realtime.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.realtime.dto.NotificationRealtimeMessage;
import com.team04.mopl.sse.event.SseEventNames;

@ExtendWith(MockitoExtension.class)
class RedisNotificationRealtimePublisherTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private RedisNotificationRealtimePublisher realtimePublisher;

	@Test
	@DisplayName("알림 실시간 전송 요청을 Redis Pub/Sub 채널로 발행한다.")
	void publish_publishNotificationRealtimeRequest_whenNotificationDtoIsGiven() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		String message = "{\"message\":\"Notification Publish\"}";

		NotificationDto notificationDto = createNotificationDto(receiverId);
		NotificationRealtimeMessage notificationRealtimeMessage = createNotificationRealtimeMessage(notificationDto);

		when(objectMapper.writeValueAsString(notificationRealtimeMessage))
			.thenReturn(message);

		// when
		realtimePublisher.publish(notificationDto);

		// then
		ArgumentCaptor<NotificationRealtimeMessage> messageCaptor =
			ArgumentCaptor.forClass(NotificationRealtimeMessage.class);
		verify(objectMapper).writeValueAsString(messageCaptor.capture());

		NotificationRealtimeMessage capturedMessage = messageCaptor.getValue();
		assertEquals(notificationDto.receiverId(), capturedMessage.receiverId());
		assertEquals(notificationDto.id(), capturedMessage.eventId());
		assertEquals(SseEventNames.NOTIFICATIONS, capturedMessage.eventName());
		assertEquals(notificationDto, capturedMessage.data());

		verify(stringRedisTemplate).convertAndSend(
			RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME,
			message
		);
	}

	@Test
	@DisplayName("알림 메시지 직렬화에 실패하면 예외가 발생하고 Redis에 발행하지 않는다.")
	void publish_throwException_whenMessageSerializationFailed() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();

		NotificationDto notificationDto = createNotificationDto(receiverId);
		NotificationRealtimeMessage notificationRealtimeMessage = createNotificationRealtimeMessage(notificationDto);

		JsonProcessingException exception = new JsonProcessingException("직렬화 실패") {
		};

		when(objectMapper.writeValueAsString(notificationRealtimeMessage))
			.thenThrow(exception);

		// when
		assertThrows(IllegalStateException.class,
			() -> realtimePublisher.publish(notificationDto));

		// then
		verify(stringRedisTemplate, never()).convertAndSend(
			anyString(),
			anyString()
		);
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

	private NotificationRealtimeMessage createNotificationRealtimeMessage(NotificationDto notificationDto) {
		return new NotificationRealtimeMessage(
			notificationDto.receiverId(),
			notificationDto.id(),
			SseEventNames.NOTIFICATIONS,
			notificationDto
		);
	}
}