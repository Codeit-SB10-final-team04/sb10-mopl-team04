package com.team04.mopl.auth.service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.auth.service.mail.TemporaryPasswordMailSender;

import lombok.RequiredArgsConstructor;

// 임시 비밀번호 발급 이벤트를 받아 이메일을 발송하는 리스너
@Component
@RequiredArgsConstructor
public class TemporaryPasswordIssuedEventListener {

	private final TemporaryPasswordMailSender temporaryPasswordMailSender;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendTemporaryPasswordMail(TemporaryPasswordIssuedEvent event) {
		temporaryPasswordMailSender.sendTemporaryPassword(
			event.email(),
			event.temporaryPassword(),
			event.expiresAt()
		);
	}
}
