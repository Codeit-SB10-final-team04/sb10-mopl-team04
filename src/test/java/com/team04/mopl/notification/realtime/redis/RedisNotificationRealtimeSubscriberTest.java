package com.team04.mopl.notification.realtime.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.realtime.dto.NotificationRealtimeMessage;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.service.SseService;

@ExtendWith(MockitoExtension.class)
class RedisNotificationRealtimeSubscriberTest {

	@Mock
	private SseService sseService;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private RedisNotificationRealtimeSubscriber subscriber;

	@Test
	@DisplayName("알림 실시간 전송 요청을 수신하면 SSE 메시지를 전송한다.")
	void onMessage_sendSse_whenNotificationRealtimeRequestReceived() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		String body = "{}";
		Message message = new DefaultMessage(
			RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME.getBytes(StandardCharsets.UTF_8),
			body.getBytes(StandardCharsets.UTF_8)
		);

		NotificationDto notificationDto = createNotificationDto(receiverId);
		NotificationRealtimeMessage realtimeMessage = createNotificationRealtimeMessage(notificationDto);

		when(objectMapper.readValue(body, NotificationRealtimeMessage.class))
			.thenReturn(realtimeMessage);

		// when
		subscriber.onMessage(message, null);

		// then
		verify(objectMapper).readValue(body, NotificationRealtimeMessage.class);
		verify(sseService).sendToReceiver(
			realtimeMessage.receiverId(),
			realtimeMessage.eventId(),
			realtimeMessage.eventName(),
			realtimeMessage.data()
		);
	}

	@Test
	@DisplayName("알림 메시지 역직렬화에 실패하면 SSE 전송을 하지 않는다.")
	void onMessage_doNotSendSseEvent_whenMessageDeserializationFailed() throws Exception {
		// given
		String body = "{}";
		Message message = new DefaultMessage(
			RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME.getBytes(StandardCharsets.UTF_8),
			body.getBytes(StandardCharsets.UTF_8)
		);

		JsonProcessingException exception = new JsonProcessingException("역직렬화 실패") {
		};

		when(objectMapper.readValue(body, NotificationRealtimeMessage.class))
			.thenThrow(exception);

		// when
		assertDoesNotThrow(() -> subscriber.onMessage(message, null));

		// then
		verify(objectMapper).readValue(body, NotificationRealtimeMessage.class);
		verify(sseService, never()).sendToReceiver(
			any(UUID.class),
			any(UUID.class),
			anyString(),
			any()
		);
	}

	@Test
	@DisplayName("SSE 전송 중 예외가 발생해도 예외를 전파하지 않는다.")
	void onMessage_doNotThrowException_whenSseSendFailed() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		String body = "{}";
		Message message = new DefaultMessage(
			RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME.getBytes(StandardCharsets.UTF_8),
			body.getBytes(StandardCharsets.UTF_8)
		);

		NotificationDto notificationDto = createNotificationDto(receiverId);
		NotificationRealtimeMessage realtimeMessage = createNotificationRealtimeMessage(notificationDto);

		RuntimeException exception = new RuntimeException("SseSend Failed");

		when(objectMapper.readValue(body, NotificationRealtimeMessage.class))
			.thenReturn(realtimeMessage);
		doThrow(exception).
			when(sseService)
			.sendToReceiver(
				realtimeMessage.receiverId(),
				realtimeMessage.eventId(),
				realtimeMessage.eventName(),
				realtimeMessage.data()
			);

		// when
		subscriber.onMessage(message, null);

		// then
		verify(objectMapper).readValue(body, NotificationRealtimeMessage.class);
		verify(sseService).sendToReceiver(
			realtimeMessage.receiverId(),
			realtimeMessage.eventId(),
			realtimeMessage.eventName(),
			realtimeMessage.data()
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