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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.sse.dto.SseMessage;
import com.team04.mopl.sse.repository.SseEmitterRepository;
import com.team04.mopl.sse.repository.SseMessageRepository;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

	@Mock
	private SseEmitterRepository sseEmitterRepository;

	@Mock
	private SseMessageRepository sseMessageRepository;

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
		verify(sseMessageRepository, never()).findAllAfterLastEventId(eq(receiverId), any(UUID.class));
	}

	@Test
	@DisplayName("lastEventId가 있으면 누락 이벤트 조회를 시도한다.")
	void connect_restoreLastEvents_whenLastEventIdIsExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		when(sseMessageRepository.findAllAfterLastEventId(receiverId, lastEventId))
			.thenReturn(List.of());

		// when
		SseEmitter result = sseService.connect(receiverId, lastEventId);

		// then
		assertNotNull(result);

		verify(sseEmitterRepository).add(eq(receiverId), any(SseEmitter.class));
		verify(sseMessageRepository).findAllAfterLastEventId(receiverId, lastEventId);
	}

	@Test
	@DisplayName("특정 수신자에게 SSE 이벤트를 전송하고 메시지를 저장한다.")
	void sendToReceiver_saveSseMessageAndSendEvent_whenSseEmitterIsExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		SseMessage expectedSseMessage = new SseMessage(eventId, receiverId, eventName, data);
		SseEmitter sseEmitter = mock(SseEmitter.class);

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of(sseEmitter));

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		ArgumentCaptor<SseMessage> sseMessageCaptor = ArgumentCaptor.forClass(SseMessage.class);
		verify(sseMessageRepository).save(sseMessageCaptor.capture());

		assertEquals(expectedSseMessage, sseMessageCaptor.getValue());

		verify(sseEmitterRepository).findAllByReceiverId(receiverId);
		verify(sseEmitterRepository, never()).remove(any(UUID.class), any(SseEmitter.class));
	}

	@Test
	@DisplayName("연결된 SseEmitter가 없어도 메시지를 저장한다.")
	void sendToReceiver_saveSseMessage_whenSseEmitterIsNotExists() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String eventName = "notifications";
		String data = "data";

		SseMessage expectedSseMessage = new SseMessage(eventId, receiverId, eventName, data);

		when(sseEmitterRepository.findAllByReceiverId(receiverId))
			.thenReturn(List.of());

		// when
		sseService.sendToReceiver(receiverId, eventId, eventName, data);

		// then
		ArgumentCaptor<SseMessage> sseMessageCaptor = ArgumentCaptor.forClass(SseMessage.class);
		verify(sseMessageRepository).save(sseMessageCaptor.capture());

		assertEquals(expectedSseMessage, sseMessageCaptor.getValue());

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

		SseMessage expectedSseMessage = new SseMessage(eventId, receiverId, eventName, data);
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
		ArgumentCaptor<SseMessage> sseMessageCaptor = ArgumentCaptor.forClass(SseMessage.class);
		verify(sseMessageRepository).save(sseMessageCaptor.capture());

		assertEquals(expectedSseMessage, sseMessageCaptor.getValue());

		verify(sseEmitterRepository).findAllByReceiverId(receiverId);
		verify(sseEmitterRepository).remove(receiverId, sseEmitter);
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