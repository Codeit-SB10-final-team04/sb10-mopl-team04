package com.team04.mopl.sse.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.sse.dto.SseMessage;
import com.team04.mopl.sse.repository.SseEmitterRepository;
import com.team04.mopl.sse.repository.SseMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 사용자별 SseEmitter 객체를 생성하고, 메시지를 전송하는 컴포넌트
@Service
@Slf4j
@RequiredArgsConstructor
public class SseService {

	private final SseEmitterRepository sseEmitterRepository;
	private final SseMessageRepository sseMessageRepository;

	// SSE 연결 유지 시간 (현재: 1시간)
	private static final long TIMEOUT = 60L * 60L * 1000L;

	// SseEmitter 객체 생성
	public SseEmitter connect(UUID receiverId, UUID lastEventId) {
		SseEmitter sseEmitter = new SseEmitter(TIMEOUT);

		// 콜백 등록: 정상적으로 연결이 끝났을 경우 실행됨
		sseEmitter.onCompletion(() -> {
			log.info("[SSE_CONNECT_COMPLETE] SSE 연결 종료 요청 완료: receiverId={}", receiverId);
			sseEmitterRepository.remove(receiverId, sseEmitter);
		});

		// 콜백 등록: SseEmitter 생성 시 지정한 TIMEOUT을 넘겼을 경우 실행됨
		sseEmitter.onTimeout(() -> {
			log.warn("[SSE_TIMEOUT] SSE 타임아웃 발생: receiverId={}", receiverId);
			sseEmitterRepository.remove(receiverId, sseEmitter);
		});

		// 콜백 등록: 네트워크 오류, 브라우저 종료, 전송 실패 등 비정상 상황일 경우 실행
		sseEmitter.onError(e -> {
			log.error("[SSE_ERROR] SSE 오류 발생: receiverId={}, error={}",
				receiverId, e.getMessage(), e);
			sseEmitterRepository.remove(receiverId, sseEmitter);
		});

		sseEmitterRepository.add(receiverId, sseEmitter);

		// 최초 연결 또는 만료 여부를 확인하기 위한 용도로 더미 이벤트를 보냄
		if (!ping(sseEmitter)) {
			sseEmitterRepository.remove(receiverId, sseEmitter);
			return sseEmitter;
		}

		// 유실된 이벤트 복원
		if (lastEventId != null) {
			restoreLostEvents(receiverId, lastEventId, sseEmitter);
		}

		return sseEmitter;
	}

	// 특정 클라이언트에게 이벤트 전송
	public void sendToReceiver(UUID receiverId, UUID eventId, String eventName, Object data) {
		SseMessage sseMessage = new SseMessage(
			eventId,
			receiverId,
			eventName,
			data
		);

		sseMessageRepository.save(sseMessage);

		for (SseEmitter sseEmitter : sseEmitterRepository.findAllByReceiverId(receiverId)) {
			try {
				sendEvent(sseEmitter, eventId, eventName, data);
			} catch (Exception e) {
				log.warn("[SSE_SEND_FAILED] SSE 전송 실패: receiverId={}, eventId={}",
					receiverId, eventId, e);
				sseEmitter.completeWithError(e);
				sseEmitterRepository.remove(receiverId, sseEmitter);
			}
		}
	}

	// 주기적으로 ping을 보내 만료된 SseEmitter 객체 삭제
	@Scheduled(fixedDelay = 1000 * 60 * 30)
	public void cleanUp() {
		sseEmitterRepository.findAll()
			.forEach((receiverId, sseEmitterList) -> {
					for (SseEmitter sseEmitter : sseEmitterList) {
						if (!ping(sseEmitter)) {
							sseEmitterRepository.remove(receiverId, sseEmitter);
						}
					}
				}
			);
	}

	// 최초 연결 또는 만료 여부를 확인하기 위한 용도로 더미 이벤트를 보냄
	private boolean ping(SseEmitter sseEmitter) {
		try {
			sseEmitter.send(
				SseEmitter.event()
					.name("connected")
			);

			return true;
		} catch (Exception e) {
			// SSE 요청을 오류 때문에 종료한다고 Spring MVC에 알림
			// -> Spring MVC가 예외를 예외 처리 메커니즘으로 전달
			sseEmitter.completeWithError(e);
			return false;
		}
	}

	private void restoreLostEvents(UUID receiverId, UUID lastEventId, SseEmitter sseEmitter) {
		// lastEventId 이후의 이벤트 가져오기
		List<SseMessage> afterSseMessageList = sseMessageRepository.findAllAfterLastEventId(receiverId, lastEventId);

		for (SseMessage sseMessage : afterSseMessageList) {
			try {
				sendEvent(
					sseEmitter,
					sseMessage.id(),
					sseMessage.eventName(),
					sseMessage.data()
				);
			} catch (Exception e) {
				log.warn("[SSE_SEND_FAILED] SSE 전송 실패: receiverId={}, eventId={}",
					receiverId, sseMessage.id(), e);
				sseEmitter.completeWithError(e);
				sseEmitterRepository.remove(receiverId, sseEmitter);
				return;
			}
		}
	}

	private void sendEvent(
		SseEmitter sseEmitter,
		UUID eventId,
		String eventName,
		Object data
	) throws IOException {
		sseEmitter.send(
			SseEmitter.event()
				.id(eventId.toString())
				.name(eventName)
				.data(data)
		);
	}
}
