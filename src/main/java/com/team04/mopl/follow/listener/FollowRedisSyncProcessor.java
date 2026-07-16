package com.team04.mopl.follow.listener;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;
import com.team04.mopl.follow.redis.FollowRedisStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRedisSyncProcessor {

	private static final String DLQ_TOPIC = "follow-redis-sync-dlq";

	private final FollowRedisStore followRedisStore;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnFollowCreated(FollowCreatedEvent followCreatedEvent) {
		try {
			// 팔로우 저장 (Redis)
			followRedisStore.addFollow(followCreatedEvent.followerId(), followCreatedEvent.followeeId());

			log.info("[REDIS_SYNC] 팔로우 생성 Redis 동기화 완료: followerId={}, followeeId={}",
				followCreatedEvent.followerId(), followCreatedEvent.followeeId());
		} catch (Exception e) {
			log.error("[REDIS_SYNC] 팔로우 생성 Redis 동기화 실패: followerId={}, followeeId={}",
				followCreatedEvent.followerId(), followCreatedEvent.followeeId(), e);

			throw e;
		}
	}

	@Recover
	public void recoverCreateFailure(Exception e, FollowCreatedEvent followCreatedEvent) {
		try {
			log.error("[REDIS_SYNC] 팔로우 생성 Redis 동기화 최종 실패: followerId={}, followeeId={}, 원인={}",
				followCreatedEvent.followerId(), followCreatedEvent.followeeId(), e.getMessage());

			// 작업 저장: 5초 타임아웃 (스레드 풀 방지)
			kafkaTemplate.send(DLQ_TOPIC, followCreatedEvent.followerId().toString(), followCreatedEvent)
				.get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] 팔로우 생성 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] 팔로우 생성 Kafka DLQ 발행 실패",
				kafkaException);
		}
	}

	@Retryable(
		value = Exception.class,
		exclude = {NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncRedisOnFollowDeleted(FollowDeletedEvent followDeletedEvent) {
		try {
			// 팔로우 삭제 (Redis)
			followRedisStore.removeFollow(followDeletedEvent.followeeId(), followDeletedEvent.followerId());

			log.info("[REDIS_SYNC] 팔로우 취소 Redis 동기화 완료: followerId={}, followeeId={}",
				followDeletedEvent.followerId(), followDeletedEvent.followeeId());
		} catch (Exception e) {
			log.error("[REDIS_SYNC] 팔로우 취소 Redis 동기화 실패: followerId={}, followeeId={}",
				followDeletedEvent.followerId(), followDeletedEvent.followeeId(), e);

			throw e;
		}
	}

	@Recover
	public void recoverDeleteFailure(Exception e, FollowDeletedEvent followDeletedEvent) {
		try {
			log.error("[REDIS_SYNC] 팔로우 취소 Redis 동기화 최종 실패: followerId={}, followeeId={}, 원인={}",
				followDeletedEvent.followerId(), followDeletedEvent.followeeId(), e.getMessage());

			// 작업 저장: 5초 타임아웃 (스레드 풀 방지)
			kafkaTemplate.send(DLQ_TOPIC, followDeletedEvent.followerId().toString(), followDeletedEvent)
				.get(5, TimeUnit.SECONDS);

			log.info("[REDIS_SYNC] 팔로우 취소 Kafka DLQ 발행 완료: topic={}",
				DLQ_TOPIC);
		} catch (Exception kafkaException) {
			log.error("[REDIS_SYNC] 팔로우 취소 Kafka DLQ 발행 실패",
				kafkaException);
		}
	}
}
