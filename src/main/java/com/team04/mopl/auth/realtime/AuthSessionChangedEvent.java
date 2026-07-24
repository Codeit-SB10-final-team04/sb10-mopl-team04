package com.team04.mopl.auth.realtime;

import java.util.Objects;
import java.util.UUID;

// 인증 세션이 변경된 사용자 ID 전달용 이벤트
public record AuthSessionChangedEvent(UUID userId) {

	public AuthSessionChangedEvent {
		Objects.requireNonNull(userId, "userId는 필수입니다.");
	}
}
