package com.team04.mopl.watching.event;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 시청 세션 변경 이벤트를 수신하여 구독자에게 broadcast
// SimpMessagingTemplate → WebSocketConfig 순환 의존성을 피하기 위해 이벤트로 분리
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {

	private final SimpMessagingTemplate messagingTemplate;

	@EventListener
	public void handleWatchingSessionEvent(WatchingSessionEvent event) {
		messagingTemplate.convertAndSend(
			"/sub/contents/" + event.contentId() + "/watch",
			event.change()
		);
	}
}
