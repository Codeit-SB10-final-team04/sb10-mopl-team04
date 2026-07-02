package com.team04.mopl.auth.service.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JavaMailSender를 사용해 임시 비밀번호 이메일을 발송하는 SMTP
 *
 * - SMTP 연결 정보: spring.mail.* 설정 사용
 * - 발신자 주소, 발신자 이름: mopl.mail.* 설정 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryPasswordMailSender {

	private final JavaMailSender javaMailSender;
	private final TemporaryPasswordMailTemplate temporaryPasswordMailTemplate;

	@Value("${mopl.mail.from}")
	private String from;

	@Value("${mopl.mail.from-name}")
	private String fromName;

	// 임시 비밀번호와 만료 시각을 이메일로 전송
	public void sendTemporaryPassword(
		String email,
		String temporaryPassword,
		Instant expiresAt
	) {
		try {
			// JavaMailSender를 통해 MIME 이메일 메시지를 생성
			MimeMessage message = javaMailSender.createMimeMessage();

			// UTF-8 HTML 메일을 작성하기 위한 helper
			MimeMessageHelper helper = new MimeMessageHelper(
				message,
				false,
				StandardCharsets.UTF_8.name()
			);

			// mopl.mail 설정을 발신자 정보로 사용
			helper.setFrom(new InternetAddress(
				from,
				fromName,
				StandardCharsets.UTF_8.name()
			));

			// 사용자가 비밀번호 초기화를 요청한 주소로 이메일 발송
			helper.setTo(email);
			helper.setSubject("임시 비밀번호 발급 - 모두의 플리");

			// TemporaryPasswordMailTemplate에서 생성한 본문으로 이메일 발송
			helper.setText(
				temporaryPasswordMailTemplate.create(temporaryPassword, expiresAt),
				true
			);

			// 실제 SMTP 서버로 이메일 발송
			javaMailSender.send(message);
		} catch (MailException | MessagingException | UnsupportedEncodingException exception) {
			log.warn("[AUTH_RESET_PASSWORD] 임시 비밀번호 이메일 전송 실패 - 재시도 대상: email={}", maskEmail(email), exception);
			throw new AuthException(AuthErrorCode.AUTH_MAIL_SEND_FAILED, exception);
		}
	}

	// 로그 이메일 마스킹 처리
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
