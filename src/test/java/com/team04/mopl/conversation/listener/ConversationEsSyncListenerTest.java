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
import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

@ExtendWith(MockitoExtension.class)
class ConversationEsSyncListenerTest {

	@Mock
	private ConversationEsSyncProcessor processor;

	@InjectMocks
	private ConversationEsSyncListener listener;

	@Test
	@DisplayName("성공: 다이렉트 메시지 전송 이벤트 수신 시 프로세서의 동기화 메서드를 호출한다.")
	void onMessageSent_DelegatesToProcessor() {
		// given
		DirectMessageSentEvent event = new DirectMessageSentEvent(UUID.randomUUID(), UUID.randomUUID(), "테스트");

		// when
		listener.onMessageSent(event);

		// then
		verify(processor, times(1)).syncMessageToElasticsearch(event);
	}

	@Test
	@DisplayName("성공: 대화방 생성 이벤트 수신 시 프로세서의 초기 문서 생성 메서드를 호출한다.")
	void onConversationCreated_DelegatesToProcessor() {
		// given
		ConversationCreatedEvent event = new ConversationCreatedEvent(UUID.randomUUID(), List.of(UUID.randomUUID()),
			Instant.now());

		// when
		listener.onConversationCreated(event);

		// then
		verify(processor, times(1)).createConversationDocument(event);
	}
}