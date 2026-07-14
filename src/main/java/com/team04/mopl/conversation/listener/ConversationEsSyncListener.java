package com.team04.mopl.conversation.listener;

import java.util.Map;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationEsSyncListener {

	private static final int MAX_MESSAGE_HISTORY_SIZE = 100;
	private static final String DLQ_TOPIC = "conversation-es-sync-dlq";

	private final ElasticsearchOperations elasticsearchOperations;

	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Retryable(
		value = Exception.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncMessageToElasticsearch(DirectMessageSentEvent event) {
		try {
			log.info("[ES_SYNC] 메시지 동기화 시작: conversationId={}, messageId={}",
				event.conversationId(), event.messageId());

			// 1. 자바 스크립트 작성: Document 내 메시지 목록(messageContents)에 원소 추가 및 LRU 정책 적용
			String script = """
				ctx._source.messageContents.add(params.newMessage);
				if (ctx._source.messageContents.size() > params.maxSize) {
				    ctx._source.messageContents.remove(0);
				}
				""";

			// 2. 쿼리 작성
			UpdateQuery updateQuery = UpdateQuery.builder(event.conversationId().toString())
				.withScript(script)
				.withParams(Map.of(
					"newMessage", event.content(),
					"maxSize", MAX_MESSAGE_HISTORY_SIZE
				))
				.build();

			// 3. 쿼리 전송
			elasticsearchOperations.update(updateQuery, IndexCoordinates.of("conversations"));

			log.info("[ES_SYNC] 메시지 동기화 완료: conversationId={}, messageId={}",
				event.conversationId(), event.messageId());
		} catch (Exception e) {
			log.error("[ES_SYNC] 메시지 동기화 실패: conversationId={}, messageId={}",
				event.conversationId(), event.messageId(), e);
			throw e;
		}
	}

	// 동기화 실패 처리
	@Recover
	public void recoverSyncFailure(Exception e, DirectMessageSentEvent event) {
		try {
			String errorMessage = e.getMessage() != null
				? e.getMessage()
				: "Unknown Error";

			log.error("[ES_SYNC] 메시지 동기화 최종 실패: conversationId={}, messageId={}, 실패 원인={}",
				event.conversationId(), event.messageId(), errorMessage);

			// DLQ 토픽으로 원본 이벤트 전송 및 대기
			kafkaTemplate.send(DLQ_TOPIC, event.conversationId().toString(), event).get();

			log.info("[ES_SYNC_DLQ_PUBLISHED] Kafka DLQ 발행 완료: messageId={}, topic={}",
				event.messageId(), DLQ_TOPIC);

		} catch (Exception kafkaException) {
			log.error("[ES_SYNC] Kafka DLQ 발행 실패: messageId={}",
				event.messageId(), kafkaException);
		}
	}
}
