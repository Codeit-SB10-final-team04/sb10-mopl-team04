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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageRedisSyncProcessor {

	private static final String DLQ_TOPIC = "direct-message-sync-dlq";

	private final DirectMessageRedisStore directMessageRedisStore;
	private final KafkaTemplate<String, Object> kafkaTemplate;

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
				directMessageCreatedEvent.directMessageDto()
			);

			log.info("[REDIS_SYNC] DM 생성 Redis 동기화 완료: directMessageId={}",
				directMessageCreatedEvent.directMessageId());
		} catch (Exception e) {
			log.error("[REDIS_SYNC] DM 생성 Redis 동기화 실패: directMessageId={}",
				directMessageCreatedEvent.directMessageId(), e);

			throw e;
		}
	}

	@Recover
	public void recoverCreateFailure(Exception e, DirectMessageCreatedEvent directMessageCreatedEvent) {
		try {
			log.error("[REDIS_SYNC] DM 생성 Redis 동기화 최종 실패 및 DLQ 발행: directMessageId={}, 원인={}",
				directMessageCreatedEvent.directMessageId(), e.getMessage());

			// 작업 저장: 5초 타임아웃 (스레드 풀 방지)
			kafkaTemplate.send(DLQ_TOPIC, directMessageCreatedEvent.directMessageId().toString())
				.get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] DM 생성 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] DM 생성 Kafka DLQ 발행 실패",
				kafkaException);
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
		} catch (Exception e) {
			log.error("[REDIS_SYNC] DM 읽음 처리 Redis 동기화 실패: directMessageId={}, receiverId={}",
				directMessageReadEvent.directMessageId(), directMessageReadEvent.receiverId(), e);

			throw e;
		}
	}

	@Recover
	public void recoverReadFailure(Exception e, DirectMessageReadEvent directMessageReadEvent) {
		log.error("[REDIS_SYNC] DM 읽음 처리 Redis 동기화 최종 실패: receiverId={}, conversationId={}, 원인={}",
			directMessageReadEvent.receiverId(), directMessageReadEvent.conversationId(), e.getMessage());
	}
}