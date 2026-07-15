package com.team04.mopl.directmessage.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DirectMessageRedisSyncListener {

	private final DirectMessageRedisSyncProcessor directMessageRedisSyncProcessor;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDirectMessageCreated(DirectMessageCreatedEvent directMessageCreatedEvent) {
		directMessageRedisSyncProcessor.syncRedisOnDirectMessageCreated(directMessageCreatedEvent);
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDirectMessageReadForRedis(DirectMessageReadEvent directMessageReadEvent) {
		directMessageRedisSyncProcessor.syncRedisOnDirectMessageRead(directMessageReadEvent);
	}

}
