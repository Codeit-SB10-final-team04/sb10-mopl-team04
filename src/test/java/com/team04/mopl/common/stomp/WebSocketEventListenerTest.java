package com.team04.mopl.common.stomp;

import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private Counter counter;

	@InjectMocks
	private WebSocketEventListener webSocketEventListener;

	@Test
	@DisplayName("웹소켓 연결 성공 시 카운터가 증가해야 한다")
	void handleWebSocketConnectListenerTest() {
		// given
		when(meterRegistry.gauge(anyString(), any(AtomicInteger.class)))
			.thenReturn(new AtomicInteger(0));
		webSocketEventListener.init();

		SessionConnectedEvent event = mock(SessionConnectedEvent.class);
		when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);

		// when
		webSocketEventListener.handleWebSocketConnectListener(event);

		// then
		verify(counter, times(1)).increment();
	}
}
