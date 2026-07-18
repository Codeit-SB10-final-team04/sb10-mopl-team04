package com.team04.mopl.conversation.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationEsSyncListener {

	private final ConversationEsSyncProcessor conversationEsSyncProcessor;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationCreated(ConversationCreatedEvent event) {
		conversationEsSyncProcessor.createConversationDocument(event);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessageSent(DirectMessageSentEvent event) {
		conversationEsSyncProcessor.syncMessageToElasticsearch(event);
	}
}
