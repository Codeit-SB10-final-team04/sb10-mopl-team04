package com.team04.mopl.notification.realtime.redis;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.notification.realtime.dto.NotificationRealtimeMessage;
import com.team04.mopl.notification.realtime.redis.metrics.RedisPubSubMetrics;
import com.team04.mopl.sse.service.SseService;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Redis Pub/Sub 채널에서 발행된 알림 실시간 전송 요청을 수신하는 Subscriber
@Component
@ConditionalOnProperty(
	name = "notification.realtime.mode",
	havingValue = "redis"
)
@Slf4j
@RequiredArgsConstructor
public class RedisNotificationRealtimeSubscriber implements MessageListener {

	private final SseService sseService;
	private final ObjectMapper objectMapper;

	private final RedisPubSubMetrics redisPubSubMetrics;

	// Redis 채널에서 메시지가 발행될 때 Listener Container에 의해 호출됨
	@Override
	public void onMessage(Message message, @Nullable byte[] pattern) {
		redisPubSubMetrics.recordReceive();

		Timer.Sample sample = redisPubSubMetrics.startTimer();
		String result = "failure";

		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			NotificationRealtimeMessage realtimeMessage =
				objectMapper.readValue(body, NotificationRealtimeMessage.class);

			sseService.sendToReceiver(
				realtimeMessage.receiverId(),
				realtimeMessage.eventId(),
				realtimeMessage.eventName(),
				realtimeMessage.data()
			);

			result = "success";
		} catch (JsonProcessingException e) {
			redisPubSubMetrics.recordDeserializationFailure();

			log.warn("[NOTIFICATION_REALTIME_MESSAGE_DESERIALIZE_FAILED] Redis 알림 메시지 역직렬화 실패", e);
		} catch (Exception e) {
			log.warn("[NOTIFICATION_REALTIME_SUBSCRIBE_FAILED] Redis 알림 메시지 처리 실패", e);
		} finally {
			redisPubSubMetrics.recordProcess(sample, result);
		}
	}
}
