package com.team04.mopl.directmessage.listener;

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

import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class DirectMessageDlqConsumerTest {

	private SimpleMeterRegistry meterRegistry;

	private DirectMessageDlqConsumer dlqConsumer;

	@Mock
	private Acknowledgment acknowledgment;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		dlqConsumer = new DirectMessageDlqConsumer(meterRegistry);
	}

	@Test
	@DisplayName("DM 생성 DLQ 이벤트 수신 시 'create' 메트릭이 증가하고 ack 처리가 되어야 한다")
	void consumeCreateDlqEvent_Success() {
		// given
		DirectMessageCreatedEvent event = mock(DirectMessageCreatedEvent.class);
		when(event.directMessageId()).thenReturn(UUID.randomUUID());

		// when
		dlqConsumer.consumeCreateDlqEvent(event, acknowledgment);

		// then
		verify(acknowledgment, times(1)).acknowledge();

		double count = meterRegistry.get("mopl.dm.redis.sync.final.failure")
			.tag("operation", "create")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("DM 읽음 DLQ 이벤트 수신 시 'read' 메트릭이 증가하고 ack 처리가 되어야 한다")
	void consumeReadDlqEvent_Success() {
		// given
		DirectMessageReadEvent event = mock(DirectMessageReadEvent.class);
		when(event.directMessageId()).thenReturn(UUID.randomUUID());
		when(event.receiverId()).thenReturn(UUID.randomUUID());

		// when
		dlqConsumer.consumeReadDlqEvent(event, acknowledgment);

		// then
		verify(acknowledgment, times(1)).acknowledge();

		double count = meterRegistry.get("mopl.dm.redis.sync.final.failure")
			.tag("operation", "read")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("알 수 없는 이벤트 수신 시 메트릭 증가 없이 ack 처리만 되어야 한다")
	void consumeUnknown_Success() {
		// given
		Object unknownEvent = new Object();

		// when
		dlqConsumer.consumeUnknown(unknownEvent, acknowledgment);

		// then
		verify(acknowledgment, times(1)).acknowledge();

		boolean isMetricEmpty = meterRegistry.find("mopl.dm.redis.sync.final.failure").counters().isEmpty();
		assertThat(isMetricEmpty).isTrue();
	}

	@Test
	@DisplayName("메시지 처리 중 예외가 발생해도 안전하게 catch 되어 카프카 무한루프를 방지해야 한다")
	void processDlq_HandlesException() {
		// given
		DirectMessageCreatedEvent event = mock(DirectMessageCreatedEvent.class);
		doThrow(new RuntimeException("Test Exception")).when(acknowledgment).acknowledge();

		// when
		dlqConsumer.consumeCreateDlqEvent(event, acknowledgment);

		// then
		double count = meterRegistry.get("mopl.dm.redis.sync.final.failure")
			.tag("operation", "create")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}
}