package com.team04.mopl.conversation.listener;

import java.util.Map;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
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

	private final ElasticsearchOperations elasticsearchOperations;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Retryable(
		value = Exception.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2)
	)
	public void syncMessageToElasticsearch(DirectMessageSentEvent event) {
		try {
			// 1. 자바 스크립트 작성: Document 내 메시지 목록(messageContents)에 원소 추가 및 LRU 정책 적
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

			log.debug("[ES_SYNC] 대화방 ID: {} 메시지 동기화 완료", event.conversationId());
		} catch (Exception e) {
			log.error("[ES_SYNC] 대화방 ID: {} 메시지 동기화 실패", event.conversationId(), e);

			throw e;
		}
	}

	// 동기화 실패 처리
	@Recover
	public void recoverSyncFailure(Exception e, DirectMessageSentEvent event) {
		log.error("[ES_SYNC_DEAD_LETTER] 대화방 ID: {} 메시지 ES 동기화 최종 실패.", event.conversationId(), e);

		// TODO: 실패한 이벤트 데이터를 RDB의 'dlq_events' 테이블에 저장하여 추후 배치 스케줄러로 재처리하도록 구현
	}
}
