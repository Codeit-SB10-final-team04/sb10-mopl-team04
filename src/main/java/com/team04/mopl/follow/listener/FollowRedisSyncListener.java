package com.team04.mopl.follow.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FollowRedisSyncListener {

	private final FollowRedisSyncProcessor followRedisSyncProcessor;

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onFollowCreated(FollowCreatedEvent followCreatedEvent) {
		followRedisSyncProcessor.syncRedisOnFollowCreated(followCreatedEvent);
	}

	@Async("eventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onFollowDeleted(FollowDeletedEvent followDeletedEvent) {
		followRedisSyncProcessor.syncRedisOnFollowDeleted(followDeletedEvent);
	}
}
