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

import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.redis.ConversationRedisStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationRedisSyncProcessor {
	private static final String DLQ_TOPIC = "conversation-sync-dlq";

	private final ConversationRedisStore conversationRedisStore;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnConversationCreated(ConversationCreatedEvent event) {
		try {
			// 대화 참여자 목록 ID 추출
			List<UUID> participantIds = event.participantIds();

			if (event.participantIds().size() >= 2) {
				UUID user1 = participantIds.get(0);
				UUID user2 = participantIds.get(1);

				// 대화 저장 (Redis)
				conversationRedisStore.addConversation(user1, user2, event.conversationId());
			}

			// 대화 참여자 목록 저장 (Redis)
			conversationRedisStore.addParticipants(event.conversationId(), new HashSet<>(participantIds));

			log.info("[REDIS_SYNC] 대화방 생성 Redis 동기화 완료: conversationId={}",
				event.conversationId());
		} catch (Exception e) {
			log.error("[REDIS_SYNC] 대화방 생성 Redis 동기화 실패: conversationId={}",
				event.conversationId(), e);

			throw e;
		}
	}

	@Recover
	public void recoverCreateFailure(Exception e, ConversationCreatedEvent event) {
		try {
			log.error("[REDIS_SYNC] 대화방 생성 Redis 동기화 최종 실패 및 DLQ 발행: conversationId={}, 원인={}",
				event.conversationId(), e.getMessage());

			// 작업 저장: 5초 타임아웃
			kafkaTemplate.send(DLQ_TOPIC, event.conversationId().toString(), event)
				.get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] 대화방 생성 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] 대화방 생성 Kafka DLQ 발행 실패", kafkaException);
		}
	}
}
