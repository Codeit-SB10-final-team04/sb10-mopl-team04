package com.team04.mopl.auth.service.event;

import java.time.Instant;

// 임시 비밀번호 발급 완료 후 메일 발송에 사용할 이벤트
public record TemporaryPasswordIssuedEvent(
	String email,
	String temporaryPassword,
	Instant expiresAt
) {
}
