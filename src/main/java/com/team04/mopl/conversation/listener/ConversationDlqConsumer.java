package com.team04.mopl.conversation.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationDlqConsumer {

	private final MeterRegistry meterRegistry;

	// 대화 생성 최종 실패
	@KafkaListener(
		topics = "conversation-sync-dlq",
		groupId = "conversation-dlq-group",
		containerFactory = "kafkaListenerContainerFactory"
	)
	public void consumeDlqEvent(
		ConversationCreatedEvent conversationCreatedEvent,
		Acknowledgment acknowledgment
	) {
		log.error("[DLQ_CONSUMER] 대화방 생성 Redis 동기화 실패 이벤트 수신: conversationId={}",
			conversationCreatedEvent.conversationId());

		try {
			// 커스텀 메트릭 수집
			meterRegistry.counter(
					"mopl.conversation.redis.sync.final.failure",
					"operation",
					"create"
				)
				.increment();

			log.error("[DLQ_CONSUMER] 대화방 생성 Redis 동기화 최종 실패: Payload={}",
				conversationCreatedEvent);

			// 메시지 정상 소비 처리
			if (acknowledgment != null) {
				acknowledgment.acknowledge();
			}

		} catch (Exception e) {
			log.error("[DLQ_CONSUMER] 대화방 DLQ 처리 및 메트릭 수집 중 오류 발생: conversationId={}",
				conversationCreatedEvent.conversationId(), e);
		}
	}
}
