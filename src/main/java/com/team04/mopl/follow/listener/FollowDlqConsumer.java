package com.team04.mopl.follow.listener;

import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(
	topics = "follow-redis-sync-dlq",
	groupId = "follow-dlq-group",
	containerFactory = "kafkaListenerContainerFactory"
)
public class FollowDlqConsumer {

	private final MeterRegistry meterRegistry;

	// 팔로우 생성 최종 실패
	@KafkaHandler
	public void consumeFollowCreatedDlqEvent(
		FollowCreatedEvent followCreatedEvent,
		Acknowledgment acknowledgment
	) {
		log.error("[DLQ_CONSUMER] 팔로우 생성 Redis 동기화 실패 이벤트 수신: followerId={}, followeeId={}",
			followCreatedEvent.followerId(), followCreatedEvent.followeeId());

		// 커스텀 메트릭 수집
		processDlq("create", followCreatedEvent, acknowledgment);
	}

	// 팔로우 취소 최종 실패
	@KafkaHandler
	public void consumeFollowDeletedDlqEvent(
		FollowDeletedEvent followDeletedEvent,
		Acknowledgment acknowledgment
	) {
		log.error("[DLQ_CONSUMER] 팔로우 취소 Redis 동기화 실패 이벤트 수신: followerId={}, followeeId={}",
			followDeletedEvent.followerId(), followDeletedEvent.followeeId());

		// 커스텀 메트릭 수집
		processDlq("delete", followDeletedEvent, acknowledgment);
	}

	// 공통 메서드: 팔로우 생성 / 취소 이벤트가 아닌 경우, 커밋 처리
	@KafkaHandler(isDefault = true)
	public void consumeUnknown(
		Object object,
		Acknowledgment acknowledgment
	) {
		log.warn("[DLQ_CONSUMER] 알 수 없는 DLQ 이벤트 수신: {}",
			object);

		if (acknowledgment != null) {
			acknowledgment.acknowledge();
		}
	}

	// 공통 메서드: 커스텀 메트릭 수집
	private void processDlq(
		String operation,
		Object payload,
		Acknowledgment acknowledgment
	) {
		try {
			// 커스텀 메트릭 수집
			meterRegistry.counter(
					"mopl.follow.redis.sync.final.failure",
					"operation",
					operation
				)
				.increment();

			log.error("[DLQ_CONSUMER] 팔로우 Redis 동기화 최종 실패: Payload={}",
				payload);

			// 메시지 정상 소비 처리
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

		} catch (Exception e) {
			log.error("[DLQ_CONSUMER] 팔로우 DLQ 처리 및 메트릭 수집 중 오류 발생: operation={}",
				operation, e);
		}
	}
}
