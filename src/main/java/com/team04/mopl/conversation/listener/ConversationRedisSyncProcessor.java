package com.team04.mopl.conversation.listener;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.redis.ConversationRedisStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationRedisSyncProcessor {
	private static final String DLQ_TOPIC = "conversation-sync-dlq";

	private final ConversationRedisStore conversationRedisStore;

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnConversationCreated(ConversationCreatedEvent conversationCreatedEvent) {
		// 커스텀 메트릭: 처리 시간 측정 시작
		Timer.Sample sample = Timer.start(meterRegistry);

		try {
			// 대화 참여자 목록 ID 추출
			List<UUID> participantIds = conversationCreatedEvent.participantIds();

			if (conversationCreatedEvent.participantIds().size() >= 2) {
				UUID user1 = participantIds.get(0);
				UUID user2 = participantIds.get(1);

				// 대화 저장 (Redis)
				conversationRedisStore.addConversation(user1, user2, conversationCreatedEvent.conversationId());
			}

			// 대화 참여자 목록 저장 (Redis)
			conversationRedisStore.addParticipants(conversationCreatedEvent.conversationId(),
				new HashSet<>(participantIds));

			log.info("[REDIS_SYNC] 대화방 생성 Redis 동기화 완료: conversationId={}",
				conversationCreatedEvent.conversationId());

			// 커스텀 메트릭 추가: 대화방 생성 동기화 성공 처리 시간
			sample.stop(meterRegistry.timer(
				"mopl.conversation.redis.sync.duration",
				"operation", "create", "result", "success"
			));
			// 커스텀 메트릭 추가: 대화방 생성 동기화 성공
			meterRegistry.counter(
				"mopl.conversation.redis.sync",
				"operation", "create", "result", "success"
			).increment();
		} catch (Exception e) {
			log.error("[REDIS_SYNC] 대화방 생성 Redis 동기화 실패: conversationId={}",
				conversationCreatedEvent.conversationId(), e);

			// 커스텀 메트릭 추가: 대화방 생성 동기화 실패 처리 시간
			sample.stop(meterRegistry.timer(
				"mopl.conversation.redis.sync.duration",
				"operation", "create", "result", "failure"
			));
			// 커스텀 메트릭 추가: 대화방 생성 동기화 실패
			meterRegistry.counter(
				"mopl.conversation.redis.sync",
				"operation", "create", "result", "failure"
			).increment();
			// 커스텀 메트릭 추가: 대화방 생성 동기화 재시도
			meterRegistry.counter(
				"mopl.conversation.redis.sync.retry",
				"operation", "create"
			).increment();

			throw e;
		}
	}

	@Recover
	public void recoverCreateFailure(
		Exception e,
		ConversationCreatedEvent conversationCreatedEvent
	) {
		try {
			log.error("[REDIS_SYNC] 대화방 생성 Redis 동기화 최종 실패 및 DLQ 발행: conversationId={}, 원인={}",
				conversationCreatedEvent.conversationId(), e.getMessage());

			// JSON 직렬화
			String payload = objectMapper.writeValueAsString(conversationCreatedEvent);

			// 작업 저장: 5초 타임아웃
			kafkaTemplate.send(
				DLQ_TOPIC,
				conversationCreatedEvent.conversationId().toString(),
				payload
			).get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] 대화방 생성 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);

			// 커스텀 메트릭 추가: DLQ 발행 성공
			meterRegistry.counter(
				"mopl.conversation.redis.sync.dlq.publish",
				"operation", "create", "result", "success"
			).increment();
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] 대화방 생성 Kafka DLQ 발행 실패", kafkaException);

			// 커스텀 메트릭 추가: DLQ 발행 실패
			meterRegistry.counter(
				"mopl.conversation.redis.sync.dlq.publish",
				"operation", "create", "result", "failure"
			).increment();

			throw new RuntimeException("DLQ 발행 실패로 인한 이벤트 유실 방지",
				kafkaException);
		}
	}
}
