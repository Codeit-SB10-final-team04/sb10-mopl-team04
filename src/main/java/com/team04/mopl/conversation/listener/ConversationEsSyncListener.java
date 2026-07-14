package com.team04.mopl.conversation.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationEsSyncListener {

	private final ConversationEsSyncProcessor processor;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationCreated(ConversationCreatedEvent event) {
		processor.createConversationDocument(event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessageSent(DirectMessageSentEvent event) {
		// 비동기 스레드가 열린 상태에서 실제 로직을 호출합니다.
		processor.syncMessageToElasticsearch(event);
	}
}
