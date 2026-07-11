package com.team04.mopl.directmessage.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.service.SseService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DirectMessageEventListener {

	private final SseService sseService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDirectMessageCreatedEvent(DirectMessageCreatedEvent event) {
		sseService.sendToReceiver(
			event.receiverId(),
			event.directMessageId(),
			SseEventNames.DIRECT_MESSAGES,
			event.directMessageDto()
		);
	}
}