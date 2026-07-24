package com.team04.mopl.notification.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.TestPropertySource;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.realtime.NotificationRealtimePublisher;
import com.team04.mopl.notification.realtime.redis.RedisNotificationRealtimePublisher;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.repository.SseEmitterRepository;
import com.team04.mopl.support.CapturingSseEmitter;
import com.team04.mopl.support.IntegrationTestBase;

@TestPropertySource(properties = {
	"notification.realtime.mode=redis"
})
class RedisNotificationSseIntegrationTest
	extends IntegrationTestBase {

	@Autowired
	private NotificationRealtimePublisher notificationRealtimePublisher;

	@Autowired
	private RedisMessageListenerContainer listenerContainer;

	@Autowired
	private SseEmitterRepository sseEmitterRepository;

	@Test
	@DisplayName("Redis Pub/Sub 메시지가 subscriber와 SseService를 거쳐 전달된다.")
	void publish_sendsNotificationThroughRedisAndSseService() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();

		NotificationDto notification = new NotificationDto(
			notificationId,
			Instant.now(),
			receiverId,
			"Redis 실시간 알림",
			"Redis Pub/Sub을 거쳐 전송되는 알림입니다.",
			NotificationLevel.INFO
		);

		CapturingSseEmitter emitter =
			new CapturingSseEmitter();

		sseEmitterRepository.add(receiverId, emitter);

		try {
			assertThat(notificationRealtimePublisher)
				.isInstanceOf(
					RedisNotificationRealtimePublisher.class
				);

			await()
				.atMost(Duration.ofSeconds(5))
				.until(listenerContainer::isRunning);

			// when
			notificationRealtimePublisher.publish(notification);

			// then
			await()
				.atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> {
					assertThat(
						emitter.containsEvent(
							notificationId,
							SseEventNames.NOTIFICATIONS
						)
					).isTrue();

					assertThat(
						emitter.containsData(notification)
					).isTrue();
				});
		} finally {
			sseEmitterRepository.remove(receiverId, emitter);
		}
	}
}