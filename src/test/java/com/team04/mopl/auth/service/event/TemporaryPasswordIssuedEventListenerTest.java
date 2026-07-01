package com.team04.mopl.auth.service.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.service.mail.TemporaryPasswordMailSender;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordIssuedEventListenerTest {

	private static final String EMAIL = "user@example.com";
	private static final String TEMPORARY_PASSWORD = "TempPassword1";

	@Mock
	private TemporaryPasswordMailSender temporaryPasswordMailSender;

	@InjectMocks
	private TemporaryPasswordIssuedEventListener temporaryPasswordIssuedEventListener;

	@Test
	@DisplayName("임시 비밀번호 발급 이벤트를 받으면 이메일을 발송한다")
	void sendTemporaryPasswordMail_sendMail_whenEventReceived() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = new TemporaryPasswordIssuedEvent(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);

		// when
		temporaryPasswordIssuedEventListener.sendTemporaryPasswordMail(event);

		// then
		verify(temporaryPasswordMailSender).sendTemporaryPassword(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);
	}

	@Test
	@DisplayName("이메일 발송 중 예외가 발생하면 AuthException을 전파한다")
	void sendTemporaryPasswordMail_throwAuthException_whenMailSendFails() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = new TemporaryPasswordIssuedEvent(
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);

		doThrow(new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED))
			.when(temporaryPasswordMailSender)
			.sendTemporaryPassword(EMAIL, TEMPORARY_PASSWORD, expiresAt);

		// when, then
		assertThatThrownBy(() -> temporaryPasswordIssuedEventListener.sendTemporaryPasswordMail(event))
			.isInstanceOf(AuthException.class);
	}
}