package com.team04.mopl.notification.integration;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.realtime.NotificationRealtimePublisher;
import com.team04.mopl.notification.realtime.local.LocalNotificationRealtimePublisher;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.repository.SseEmitterRepository;
import com.team04.mopl.support.CapturingSseEmitter;
import com.team04.mopl.support.IntegrationTestBase;

class LocalNotificationSseIntegrationTest
	extends IntegrationTestBase {

	@Autowired
	private NotificationRealtimePublisher notificationRealtimePublisher;

	@Autowired
	private SseEmitterRepository sseEmitterRepository;

	@Test
	@DisplayName("local publisher가 실제 SseService와 Repository를 거쳐 알림을 전송한다.")
	void publish_sendsNotificationThroughLocalSseService() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();

		NotificationDto notification = new NotificationDto(
			notificationId,
			Instant.now(),
			receiverId,
			"local 실시간 알림",
			"local 모드로 전송되는 알림입니다.",
			NotificationLevel.INFO
		);

		CapturingSseEmitter emitter =
			new CapturingSseEmitter();

		sseEmitterRepository.add(receiverId, emitter);

		try {
			assertThat(notificationRealtimePublisher)
				.isInstanceOf(
					LocalNotificationRealtimePublisher.class
				);

			// when
			notificationRealtimePublisher.publish(notification);

			// then
			assertThat(
				emitter.containsEvent(
					notificationId,
					SseEventNames.NOTIFICATIONS
				)
			).isTrue();

			assertThat(
				emitter.containsData(notification)
			).isTrue();
		} finally {
			sseEmitterRepository.remove(receiverId, emitter);
		}
	}
}