package com.team04.mopl.notification.realtime;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.realtime.local.LocalNotificationRealtimePublisher;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.service.SseService;

@ExtendWith(MockitoExtension.class)
class LocalNotificationRealtimePublisherTest {

	@Mock
	private SseService sseService;

	@InjectMocks
	private LocalNotificationRealtimePublisher localNotificationRealtimePublisher;

	@Test
	@DisplayName("NotificationDto를 notifications SSE 이벤트로 전송한다.")
	void publish_sendSse_whenNotificationIsGiven() {
		// given
		UUID userId = UUID.randomUUID();

		NotificationDto notificationDto = createNotificationDto(userId);

		// when
		localNotificationRealtimePublisher.publish(notificationDto);

		// then
		verify(sseService).sendToReceiver(
			notificationDto.receiverId(),
			notificationDto.id(),
			SseEventNames.NOTIFICATIONS,
			notificationDto
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
}