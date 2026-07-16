package com.team04.mopl.conversation.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationRedisSyncListener {

	private final ConversationRedisSyncProcessor conversationRedisSyncProcessor;

	@Async("enventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationCreated(ConversationCreatedEvent conversationCreatedEvent) {
		conversationRedisSyncProcessor.syncRedisOnConversationCreated(conversationCreatedEvent);
	}
}
