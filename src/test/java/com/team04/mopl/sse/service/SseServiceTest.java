package com.team04.mopl.sse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.notification.service.NotificationRestoreService;
import com.team04.mopl.sse.repository.SseEmitterRepository;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

	@Mock
	private SseEmitterRepository sseEmitterRepository;

	@Mock
	private NotificationRestoreService notificationRestoreService;

	@InjectMocks
	private SseService sseService;

	@Test
	@DisplayName("SSE 연결 시 수신자를 기준으로 SseEmitter를 등록한다.")
	void connect_addSseEmitter_whenValidRequest() {
		// given
		UUID receiverId = UUID.randomUUID();

		// when
		SseEmitter result = sseService.connect(receiverId, null);

		// then
		assertNotNull(result);

		verify(sseEmitterRepository).add(eq(receiverId), any(SseEmitter.class));
	}

	@Test
	@DisplayName("lastEventId가 있으면 누락 이벤트 조회를 시도한다.")
	void connect_restoreLastEvents_whenLastEventIdIsExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		when(notificationRestoreService.findUnreadNotificationsAfter(receiverId, lastEventId))
			.thenReturn(List.of());

		// when
		SseEmitter result = sseService.connect(receiverId, lastEventId);

		// then
		assertNotNull(result);

		verify(sseEmitterRepository).add(eq(receiverId), any(SseEmitter.class));
		verify(notificationRestoreService).findUnreadNotificationsAfter(receiverId, lastEventId);
	}

	@Test
	@DisplayName("특정 수신자에게 SSE 이벤트를 전송한다.")
	void sendToReceiver_sendEvent_whenSseEmitterIsExists() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		SseEmitter sseEmitter = mock(SseEmitter.class);

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of(sseEmitter));

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		verify(sseEmitterRepository).findAllByReceiverId(receiverId);
		verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
		verify(sseEmitterRepository, never()).remove(any(UUID.class), any(SseEmitter.class));
	}

	@Test
	@DisplayName("연결된 SseEmitter가 없으면 아무것도 하지 않는다.")
	void sendToReceiver_doNothing_whenSseEmitterIsNotExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of());

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		verify(sseEmitterRepository).findAllByReceiverId(receiverId);
		verify(sseEmitterRepository, never()).remove(any(UUID.class), any(SseEmitter.class));
	}

	@Test
	@DisplayName("SSE 전송 실패 시 해당 SseEmitter를 제거한다.")
	void sendToReceiver_removeSseEmitter_whenSseSendFailed() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		SseEmitter sseEmitter = mock(SseEmitter.class);
		IOException exception = new IOException("SSE Send Failed");

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of(sseEmitter));
		doThrow(exception)
			.when(sseEmitter)
			.send(any(SseEmitter.SseEventBuilder.class));

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		verify(sseEmitterRepository).findAllByReceiverId(receiverId);
		verify(sseEmitterRepository).remove(receiverId, sseEmitter);
	}

	@Test
	@DisplayName("여러 SseEmitter 중 하나가 전송에 실패해도 나머지 SseEmitter에는 전송이 계속된다.")
	void sendToReceiver_continueSendSSE_whenOneSseEmitterSendFailed() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		SseEmitter sseEmitter1 = mock(SseEmitter.class);
		SseEmitter sseEmitter2 = mock(SseEmitter.class);
		IOException exception = new IOException("SSE Send Failed");

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of(sseEmitter1, sseEmitter2));
		doThrow(exception)
			.when(sseEmitter1)
			.send(any(SseEmitter.SseEventBuilder.class));

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		verify(sseEmitterRepository).findAllByReceiverId(receiverId);

		verify(sseEmitter1).send(any(SseEmitter.SseEventBuilder.class));
		verify(sseEmitterRepository).remove(receiverId, sseEmitter1);

		verify(sseEmitter2).send(any(SseEmitter.SseEventBuilder.class));
		verify(sseEmitterRepository, never()).remove(receiverId, sseEmitter2);
	}

	@Test
	@DisplayName("정리 작업 중 ping 전송에 실패한 SseEmitter를 제거한다.")
	void cleanUp_removeSseEmitter_whenPingFailed() throws Exception {
		// given
		UUID receiverId = UUID.randomUUID();

		SseEmitter sseEmitter = mock(SseEmitter.class);
		IOException exception = new IOException("SSE Ping Failed");

		when(sseEmitterRepository.findAll())
			.thenReturn(Map.of(receiverId, List.of(sseEmitter)));
		doThrow(exception)
			.when(sseEmitter)
			.send(any(SseEmitter.SseEventBuilder.class));

		// when
		sseService.cleanUp();

		// then
		verify(sseEmitterRepository).findAll();
		verify(sseEmitterRepository).remove(receiverId, sseEmitter);
	}
}