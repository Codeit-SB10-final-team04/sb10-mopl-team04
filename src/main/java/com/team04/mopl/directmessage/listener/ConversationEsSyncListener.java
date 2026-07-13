package com.team04.mopl.directmessage.listener;

import java.util.Map;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
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

	private final ElasticsearchOperations elasticsearchOperations;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void syncMessageToElasticsearch(DirectMessageSentEvent event) {
		try {
			// 1. 쿼리 작성
			UpdateQuery updateQuery = UpdateQuery.builder(event.conversationId().toString())
				// Conversation Document 내 메시지 목록(messageContents)에 원소 추가
				.withScript("ctx._source.messageContents.add(params.newMessage)")
				.withParams(Map.of("newMessage", event.content()))
				.build();

			// 2. 쿼리 전송
			elasticsearchOperations.update(updateQuery, IndexCoordinates.of("conversations"));

			log.debug("[ES_SYNC] 대화방 ID: {} 메시지 동기화 완료", event.conversationId());
		} catch (Exception e) {
			log.error("[ES_SYNC] 대화방 ID: {} 메시지 동기화 실패", event.conversationId(), e);
		}
	}
}
