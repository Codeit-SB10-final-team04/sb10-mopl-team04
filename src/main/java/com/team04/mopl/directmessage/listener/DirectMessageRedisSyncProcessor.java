package com.team04.mopl.directmessage.listener;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;
import com.team04.mopl.directmessage.redis.DirectMessageRedisStore;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageRedisSyncProcessor {

	private static final String DLQ_TOPIC = "direct-message-sync-dlq";

	private final DirectMessageRedisStore directMessageRedisStore;

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final MeterRegistry meterRegistry;

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnDirectMessageCreated(DirectMessageCreatedEvent directMessageCreatedEvent) {
		try {
			// DM 저장 (Redis)
			directMessageRedisStore.addDirectMessage(
				directMessageCreatedEvent.directMessageDto().conversationId(),
				directMessageCreatedEvent.receiverId(),
				directMessageCreatedEvent.directMessageDto()
			);

			log.info("[REDIS_SYNC] DM 생성 Redis 동기화 완료: directMessageId={}",
				directMessageCreatedEvent.directMessageId());

			// 커스텀 메트릭 추가: DM 동기화 성공
			meterRegistry.counter(
				"mopl.dm.redis.sync",
				"operation", "create", "result", "success"
			).increment();
		} catch (Exception e) {
			log.error("[REDIS_SYNC] DM 생성 Redis 동기화 실패: directMessageId={}",
				directMessageCreatedEvent.directMessageId(), e);

			// 커스텀 메트릭 추가: DM 동기화 실패 및 재시도
			meterRegistry.counter(
				"mopl.dm.redis.sync",
				"operation", "create", "result", "failure"
			).increment();
			meterRegistry.counter(
				"mopl.dm.redis.sync.retry",
				"operation", "create"
			).increment();

			throw e;
		}
	}

	@Recover
	public void recoverCreateFailure(
		Exception e,
		DirectMessageCreatedEvent directMessageCreatedEvent
	) {
		try {
			log.error("[REDIS_SYNC] DM 생성 Redis 동기화 최종 실패 및 DLQ 발행: directMessageId={}, 원인={}",
				directMessageCreatedEvent.directMessageId(), e.getMessage());

			// 작업 저장: 5초 타임아웃
			kafkaTemplate.send(
				DLQ_TOPIC,
				directMessageCreatedEvent.directMessageId().toString(),
				directMessageCreatedEvent
			).get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] DM 생성 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);

			// 커스텀 메트릭 추가: DLQ 발행 성공
			meterRegistry.counter(
				"mopl.dm.redis.sync.dlq.publish",
				"result", "success"
			).increment();
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] DM 생성 Kafka DLQ 발행 실패", kafkaException);

			// 커스텀 메트릭 추가: DLQ 발행 실패
			meterRegistry.counter(
				"mopl.dm.redis.sync.dlq.publish",
				"result", "failure"
			).increment();
		}
	}

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnDirectMessageRead(DirectMessageReadEvent directMessageReadEvent) {
		try {
			// DM 읽음 개수 감소 (Redis)
			directMessageRedisStore.decrementUnreadCount(
				directMessageReadEvent.receiverId(),
				directMessageReadEvent.conversationId()
			);

			log.info("[REDIS_SYNC] DM 읽음 처리 Redis 동기화 완료: directMessageId={}, receiverId={}",
				directMessageReadEvent.directMessageId(), directMessageReadEvent.receiverId());

			// 커스텀 메트릭 추가: DM 읽음 상태 동기화 성공
			meterRegistry.counter(
				"mopl.dm.redis.sync",
				"operation", "read", "result", "success"
			).increment();
		} catch (Exception e) {
			log.error("[REDIS_SYNC] DM 읽음 처리 Redis 동기화 실패: directMessageId={}, receiverId={}",
				directMessageReadEvent.directMessageId(), directMessageReadEvent.receiverId(), e);

			// 커스텀 메트릭 추가: DM 읽음 상태 동기화 실패 및 재시도
			meterRegistry.counter(
				"mopl.dm.redis.sync",
				"operation", "read", "result", "failure"
			).increment();
			meterRegistry.counter(
				"mopl.dm.redis.sync.retry",
				"operation", "read"
			).increment();

			throw e;
		}
	}

	@Recover
	public void recoverReadFailure(
		Exception e,
		DirectMessageReadEvent directMessageReadEvent
	) {
		try {
			log.error("[REDIS_SYNC] DM 읽음 처리 Redis 동기화 최종 실패 및 DLQ 발행: receiverId={}, conversationId={}, 원인={}",
				directMessageReadEvent.receiverId(), directMessageReadEvent.conversationId(), e.getMessage());

			// 작업 저장: 5초 타임아웃
			kafkaTemplate.send(
				DLQ_TOPIC,
				directMessageReadEvent.directMessageId().toString(),
				directMessageReadEvent
			).get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] DM 읽음 상태 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);

			// 커스텀 메트릭 추가: DLQ 발행 성공
			meterRegistry.counter(
				"mopl.dm.redis.sync.dlq.publish",
				"result", "success"
			).increment();
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] DM 읽음 상태 Kafka DLQ 발행 실패", kafkaException);

			// 커스텀 메트릭 추가: DLQ 발행 실패
			meterRegistry.counter(
				"mopl.dm.redis.sync.dlq.publish",
				"result", "failure"
			).increment();
		}
	}
}