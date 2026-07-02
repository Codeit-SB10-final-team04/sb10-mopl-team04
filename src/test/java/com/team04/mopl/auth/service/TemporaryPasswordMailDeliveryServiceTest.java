package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.service.event.TemporaryPasswordIssuedEvent;
import com.team04.mopl.auth.service.mail.TemporaryPasswordMailSender;

@SpringJUnitConfig(TemporaryPasswordMailDeliveryServiceTest.TestConfig.class)
class TemporaryPasswordMailDeliveryServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final String EMAIL = "user@example.com";
	private static final String TEMPORARY_PASSWORD = "TempPassword1";

	@MockitoBean
	private TemporaryPasswordMailSender temporaryPasswordMailSender;

	@MockitoBean
	private TemporaryPasswordCleanupService temporaryPasswordCleanupService;

	@Autowired
	private TemporaryPasswordMailDeliveryService temporaryPasswordMailDeliveryService;

	@Test
	@DisplayName("메일 발송에 성공하면 임시 비밀번호를 삭제하지 않는다")
	void deliver_sendMailAndDoNotCleanup_whenMailSendSucceeds() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = createEvent(expiresAt);

		// when
		temporaryPasswordMailDeliveryService.deliver(event);

		// then
		verify(temporaryPasswordMailSender).sendTemporaryPassword(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);
		verifyNoInteractions(temporaryPasswordCleanupService);
	}

	@Test
	@DisplayName("메일 발송이 일시적으로 실패하면 최대 3회까지 재시도한다")
	void deliver_retryAndSuccess_whenMailSendFailsTemporarily() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = createEvent(expiresAt);
		AuthException exception = new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED);

		doThrow(exception)
			.doThrow(exception)
			.doNothing()
			.when(temporaryPasswordMailSender)
			.sendTemporaryPassword(EMAIL, TEMPORARY_PASSWORD, expiresAt);

		// when
		temporaryPasswordMailDeliveryService.deliver(event);

		// then
		verify(temporaryPasswordMailSender, org.mockito.Mockito.times(3)).sendTemporaryPassword(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);
		verifyNoInteractions(temporaryPasswordCleanupService);
	}

	@Test
	@DisplayName("메일 발송이 최종 실패하면 임시 비밀번호를 삭제하고 AuthException을 전파한다")
	void deliver_cleanupAndThrowAuthException_whenMailSendFailsFinally() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = createEvent(expiresAt);
		AuthException exception = new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED);

		doThrow(exception)
			.when(temporaryPasswordMailSender)
			.sendTemporaryPassword(EMAIL, TEMPORARY_PASSWORD, expiresAt);

		doNothing()
			.when(temporaryPasswordCleanupService)
			.deleteByUserId(USER_ID);

		// when, then
		assertThatThrownBy(() -> temporaryPasswordMailDeliveryService.deliver(event))
			.isInstanceOf(AuthException.class);

		verify(temporaryPasswordMailSender, org.mockito.Mockito.times(3)).sendTemporaryPassword(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);
		verify(temporaryPasswordCleanupService).deleteByUserId(USER_ID);
	}

	private TemporaryPasswordIssuedEvent createEvent(Instant expiresAt) {
		return new TemporaryPasswordIssuedEvent(
			USER_ID,
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);
	}

	@EnableRetry
	@TestConfiguration
	@Import(TemporaryPasswordMailDeliveryService.class)
	static class TestConfig {
	}
}