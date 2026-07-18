package com.team04.mopl.sse.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.service.DirectMessageRestoreService;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.service.NotificationRestoreService;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.repository.SseEmitterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 사용자별 SseEmitter 객체를 생성하고, 메시지를 전송하는 컴포넌트
@Service
@Slf4j
@RequiredArgsConstructor
public class SseService {

	// SSE 연결 유지 시간 (현재: 1시간)
	private static final long TIMEOUT = 60L * 60L * 1000L;
	private final SseEmitterRepository sseEmitterRepository;
	private final NotificationRestoreService notificationRestoreService;
	private final DirectMessageRestoreService directMessageRestoreService;

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
	@Scheduled(fixedDelay = 1000 * 30)
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

	// 복원 메서드: 분기
	private void restoreLostEvents(UUID receiverId, UUID lastEventId, SseEmitter sseEmitter) {
		// 1. DM 복원
		boolean isDirectMessageSuccess = restoreDirectMessages(receiverId, lastEventId, sseEmitter);

		// DM 복원 도중 오류 발생 시, 알림 복원 없이 종료
		if (!isDirectMessageSuccess) {
			return;
		}

		// 2. 알림 복원
		restoreNotifications(receiverId, lastEventId, sseEmitter);
	}

	// 알림 복원 메서드
	private void restoreNotifications(UUID receiverId, UUID lastEventId, SseEmitter sseEmitter) {
		// lastEventId 이후의 이벤트 가져오기
		List<NotificationDto> afterNotificationDtoList =
			notificationRestoreService.findUnreadNotificationsAfter(receiverId, lastEventId);

		for (NotificationDto notificationDto : afterNotificationDtoList) {
			try {
				sendEvent(
					sseEmitter,
					notificationDto.id(),
					SseEventNames.NOTIFICATIONS,
					notificationDto
				);
			} catch (Exception e) {
				log.warn("[SSE_SEND_FAILED] SSE 전송 실패: receiverId={}, eventId={}",
					receiverId, notificationDto.id(), e);
				sseEmitter.completeWithError(e);
				sseEmitterRepository.remove(receiverId, sseEmitter);
				return;
			}
		}
	}

	// DM 복원 메서드
	private boolean restoreDirectMessages(UUID receiverId, UUID lastEventId, SseEmitter sseEmitter) {
		List<DirectMessageDto> missedDms = directMessageRestoreService.findUnreadMessagesAfter(receiverId, lastEventId);

		for (DirectMessageDto directMessageDto : missedDms) {
			try {
				sendEvent(
					sseEmitter,
					directMessageDto.id(),
					SseEventNames.DIRECT_MESSAGES,
					directMessageDto
				);
			} catch (Exception e) {
				log.warn("[SSE_SEND_FAILED] SSE 쪽지 전송 실패: receiverId={}, eventId={}",
					receiverId, directMessageDto.id(), e);
				sseEmitter.completeWithError(e);
				sseEmitterRepository.remove(receiverId, sseEmitter);
				return false;
			}
		}
		return true;
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
