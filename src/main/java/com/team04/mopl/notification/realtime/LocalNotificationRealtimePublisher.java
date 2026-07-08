package com.team04.mopl.notification.realtime;

import org.springframework.stereotype.Component;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.service.SseService;

import lombok.RequiredArgsConstructor;

// 현재 서버 인스턴스에 연결된 SSE 클라이언트로 알림을 전송하는 로컬 구현체
@Component
@RequiredArgsConstructor
public class LocalNotificationRealtimePublisher implements NotificationRealtimePublisher {

	private final SseService sseService;

	@Override
	public void publish(NotificationDto notificationDto) {
		sseService.sendToReceiver(
			notificationDto.receiverId(),
			notificationDto.id(),
			SseEventNames.NOTIFICATIONS,
			notificationDto
		);
	}
}
