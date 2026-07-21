package com.team04.mopl.directmessage.listener;

import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(
	topics = "direct-message-sync-dlq",
	groupId = "direct-message-dlq-group",
	containerFactory = "kafkaListenerContainerFactory"
)
public class DirectMessageDlqConsumer {

	private final MeterRegistry meterRegistry;

	// DM 생성 최종 실패
	@KafkaHandler
	public void consumeCreateDlqEvent(
		DirectMessageCreatedEvent directMessageCreatedEvent,
		Acknowledgment acknowledgment
	) {
		log.error("[DLQ_CONSUMER] DM 생성 Redis 동기화 실패 이벤트 수신: directMessageId={}",
			directMessageCreatedEvent.directMessageId());

		// 커스텀 메트릭 수집
		processDlq("create", directMessageCreatedEvent, acknowledgment);
	}

	// DM 읽음 처리 생성 최종 실패
	@KafkaHandler
	public void consumeReadDlqEvent(
		DirectMessageReadEvent directMessageReadEvent,
		Acknowledgment acknowledgment
	) {
		log.error("[DLQ_CONSUMER] DM 읽음 상태 Redis 동기화 실패 이벤트 수신: directMessageId={}, receiverId={}",
			directMessageReadEvent.directMessageId(), directMessageReadEvent.receiverId());

		// 커스텀 메트릭 수집
		processDlq("read", directMessageReadEvent, acknowledgment);
	}

	// 공통 메서드: DM / DM 읽음 상태 이벤트가 아닌 경우, 커밋 처리
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
					"mopl.dm.redis.sync.final.failure",
					"operation",
					operation
				)
				.increment();

			log.error("[DLQ_CONSUMER] DM Redis 동기화 최종 실패: Payload={}",
				payload);

			// 메시지 정상 소비 처리
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

		} catch (Exception e) {
			log.error("[DLQ_CONSUMER] DM DLQ 처리 및 메트릭 수집 중 오류 발생: operation={}",
				operation, e);
		}
	}
}