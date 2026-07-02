package com.team04.mopl.auth.service;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.service.event.TemporaryPasswordIssuedEvent;
import com.team04.mopl.auth.service.mail.TemporaryPasswordMailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 임시 비밀번호 메일 발송의 재시도, 실패 보상 처리 담당
 * - 메일 발송이 일시적으로 실패할 수 있으므로 최대 3회 재시도
 * - 모든 재시도가 실패하면 DB에 저장된 임시 비밀번호를 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemporaryPasswordMailDeliveryService {

	private final TemporaryPasswordMailSender temporaryPasswordMailSender;
	private final TemporaryPasswordCleanupService temporaryPasswordCleanupService;

	// 메일 발송 실패 시 1초, 2초 간격으로 최대 3회까지 재시도
	@Retryable(
		retryFor = AuthException.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 1000, multiplier = 2.0)
	)
	public void deliver(TemporaryPasswordIssuedEvent event) {
		log.info("[AUTH_RESET_PASSWORD] 임시 비밀번호 메일 발송 시도: email={}", maskEmail(event.email()));

		temporaryPasswordMailSender.sendTemporaryPassword(
			event.email(),
			event.temporaryPassword(),
			event.expiresAt()
		);
	}

	// 모든 재시도가 실패하면 저장된 임시 비밀번호를 삭제하고 예외 재전파
	@Recover
	public void recover(AuthException exception, TemporaryPasswordIssuedEvent event) {
		log.error(
			"[AUTH_RESET_PASSWORD] 임시 비밀번호 메일 발송 최종 실패: userId={}, email={}",
			event.userId(),
			maskEmail(event.email()),
			exception
		);

		// 사용자가 알 수 없는 임시 비밀번호가 DB에 남지 않도록 삭제
		temporaryPasswordCleanupService.deleteByUserId(event.userId());

		throw exception;
	}

	private static String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return "invalid-email";
		}

		int atIndex = email.indexOf("@");
		String localPart = email.substring(0, atIndex);
		String domain = email.substring(atIndex);

		if (localPart.length() <= 2) {
			return "*" + domain;
		}

		return localPart.charAt(0)
			+ "***"
			+ localPart.charAt(localPart.length() - 1)
			+ domain;
	}
}
