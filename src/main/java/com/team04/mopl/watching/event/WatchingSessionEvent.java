package com.team04.mopl.watching.event;

import java.util.UUID;

import com.team04.mopl.watching.dto.response.WatchingSessionChange;

// 시청 세션 변경(JOIN/LEAVE) 이벤트
// StompWatchingInterceptor에서 발행 → WatchingSessionEventListener에서 수신 → broadcast
// SimpMessagingTemplate → WebSocketConfig → 인터셉터 순환 의존성을 끊기 위해 이벤트 기반으로 분리
public record WatchingSessionEvent(
	UUID contentId,
	WatchingSessionChange change
) {
}
