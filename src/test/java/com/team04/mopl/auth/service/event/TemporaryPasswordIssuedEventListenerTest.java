package com.team04.mopl.auth.service.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.service.TemporaryPasswordMailDeliveryService;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordIssuedEventListenerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final String EMAIL = "user@example.com";
	private static final String TEMPORARY_PASSWORD = "TempPassword1";

	@Mock
	private TemporaryPasswordMailDeliveryService temporaryPasswordMailDeliveryService;

	@InjectMocks
	private TemporaryPasswordIssuedEventListener temporaryPasswordIssuedEventListener;

	@Test
	@DisplayName("임시 비밀번호 발급 이벤트를 받으면 메일 발송 서비스에 위임한다")
	void sendTemporaryPasswordMail_deliverMail_whenEventReceived() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = new TemporaryPasswordIssuedEvent(
			USER_ID,
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);

		// when
		temporaryPasswordIssuedEventListener.sendTemporaryPasswordMail(event);

		// then
		verify(temporaryPasswordMailDeliveryService).deliver(event);
	}

	@Test
	@DisplayName("메일 발송 서비스에서 예외가 발생하면 AuthException을 전파한다")
	void sendTemporaryPasswordMail_throwAuthException_whenDeliveryFails() {
		// given
		Instant expiresAt = Instant.now().plusSeconds(180);
		TemporaryPasswordIssuedEvent event = new TemporaryPasswordIssuedEvent(
			USER_ID,
			EMAIL,
			TEMPORARY_PASSWORD,
			expiresAt
		);

		doThrow(new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED))
			.when(temporaryPasswordMailDeliveryService)
			.deliver(event);

		// when, then
		assertThatThrownBy(() -> temporaryPasswordIssuedEventListener.sendTemporaryPasswordMail(event))
			.isInstanceOf(AuthException.class);
	}
}