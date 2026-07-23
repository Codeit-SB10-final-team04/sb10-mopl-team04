package com.team04.mopl.notification.realtime.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.realtime.NotificationRealtimePublisher;
import com.team04.mopl.notification.realtime.dto.NotificationRealtimeMessage;
import com.team04.mopl.notification.realtime.redis.metrics.RedisPubSubMetrics;
import com.team04.mopl.sse.event.SseEventNames;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

// 알림 실시간 전송 요청을 Redis Pub/Sub 채널로 발행하는 Publisher
@Component
@ConditionalOnProperty(
	name = "notification.realtime.mode",
	havingValue = "redis"
)
@RequiredArgsConstructor
public class RedisNotificationRealtimePublisher implements NotificationRealtimePublisher {

	// Redis 채널에 문자열 메시지를 publish할 때 사용
	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

	private final RedisPubSubMetrics redisPubSubMetrics;

	@Override
	public void publish(NotificationDto notificationDto) {
		Timer.Sample sample = redisPubSubMetrics.startTimer();
		String result = "failure";

		try {
			NotificationRealtimeMessage notificationRealtimeMessage = new NotificationRealtimeMessage(
				notificationDto.receiverId(),
				notificationDto.id(),
				SseEventNames.NOTIFICATIONS,
				notificationDto
			);

			String message = objectMapper.writeValueAsString(notificationRealtimeMessage);

			// Redis Pub/Sub 채널에 알림 실시간 전송 요청 메시지 발행
			stringRedisTemplate.convertAndSend(
				RedisNotificationRealtimeChannels.NOTIFICATION_REALTIME,
				message
			);

			result = "success";
		} catch (JsonProcessingException e) {
			// Consumer에서 예외를 잡기 때문에 런타임 예외 발생 시킴
			throw new IllegalStateException("Redis Publish 알림 메시지 직렬화에 실패했습니다.", e);
		} finally {
			redisPubSubMetrics.recordPublish(sample, result);
		}
	}
}
