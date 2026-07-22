package com.team04.mopl.conversation.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.team04.mopl.conversation.event.ConversationCreatedEvent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ConversationDlqConsumerTest {

	private SimpleMeterRegistry meterRegistry;

	private ConversationDlqConsumer dlqConsumer;

	@Mock
	private Acknowledgment acknowledgment;

	@Mock
	private ConversationCreatedEvent conversationCreatedEvent;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		dlqConsumer = new ConversationDlqConsumer(meterRegistry);
	}

	@Test
	@DisplayName("대화방 생성 DLQ 이벤트 수신 시 메트릭이 증가하고 ack 처리가 되어야 한다")
	void consumeDlqEvent_Success() {
		// given
		when(conversationCreatedEvent.conversationId()).thenReturn(UUID.randomUUID());

		// when
		dlqConsumer.consumeDlqEvent(conversationCreatedEvent, acknowledgment);

		// then
		verify(acknowledgment, times(1)).acknowledge();

		double count = meterRegistry.get("mopl.conversation.redis.sync.final.failure")
			.tag("operation", "create")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Acknowledgment가 null인 경우에도 메트릭이 수집되고 예외가 발생하지 않아야 한다")
	void consumeDlqEvent_NullAck() {
		// given
		when(conversationCreatedEvent.conversationId()).thenReturn(UUID.randomUUID());

		// when
		dlqConsumer.consumeDlqEvent(conversationCreatedEvent, null);

		// then
		double count = meterRegistry.get("mopl.conversation.redis.sync.final.failure")
			.tag("operation", "create")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("메시지 처리 중 예외가 발생해도 밖으로 던지지 않고 안전하게 catch 되어야 한다 (무한루프 방지)")
	void consumeDlqEvent_HandlesException() {
		// given
		when(conversationCreatedEvent.conversationId()).thenReturn(UUID.randomUUID());

		doThrow(new RuntimeException("Test Exception")).when(acknowledgment).acknowledge();

		// when
		dlqConsumer.consumeDlqEvent(conversationCreatedEvent, acknowledgment);

		// then
		// 예외가 밖으로 던져지지 않았는지(정상 종료) 확인하며, 메트릭은 증가했어야 함
		double count = meterRegistry.get("mopl.conversation.redis.sync.final.failure")
			.tag("operation", "create")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}
}