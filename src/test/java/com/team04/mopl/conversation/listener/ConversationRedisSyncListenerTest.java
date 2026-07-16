package com.team04.mopl.conversation.listener;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;

@ExtendWith(MockitoExtension.class)
class ConversationRedisSyncListenerTest {

	@Mock
	private ConversationRedisSyncProcessor conversationRedisSyncProcessor;

	@InjectMocks
	private ConversationRedisSyncListener conversationRedisSyncListener;

	@Test
	@DisplayName("성공: 대화 생성 이벤트가 수신되면 Processor의 syncRedisOnConversationCreated 메서드로 위임한다.")
	void onConversationCreated_DelegatesToProcessor_Success() {
		// given
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			UUID.randomUUID(),
			List.of(UUID.randomUUID(), UUID.randomUUID()),
			Instant.now()
		);

		// when
		conversationRedisSyncListener.onConversationCreated(event);

		// then
		verify(conversationRedisSyncProcessor, times(1)).syncRedisOnConversationCreated(event);
	}
}