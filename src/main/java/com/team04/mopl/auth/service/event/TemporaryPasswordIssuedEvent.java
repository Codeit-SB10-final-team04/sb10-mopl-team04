package com.team04.mopl.auth.service.event;

import java.time.Instant;
import java.util.UUID;

// 임시 비밀번호 발급 완료 후 메일 발송에 사용할 이벤트
public record TemporaryPasswordIssuedEvent(
	UUID userId,
	String email,
	String temporaryPassword,
	Instant expiresAt
) {
}
