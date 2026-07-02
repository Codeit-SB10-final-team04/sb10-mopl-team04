package com.team04.mopl.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.entity.TemporaryPassword;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
import com.team04.mopl.auth.service.event.TemporaryPasswordIssuedEvent;
import com.team04.mopl.auth.service.mail.TemporaryPasswordMailSender;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemporaryPasswordService {

	private static final long TEMPORARY_PASSWORD_EXPIRATION_SECONDS = 180L; // 3분

	private final UserRepository userRepository;
	private final TemporaryPasswordRepository temporaryPasswordRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final ApplicationEventPublisher eventPublisher;

	// 이메일 주소로 임시 비밀번호를 발급하고 이메일로 전송
	@Transactional
	public void resetPassword(String email) {
		String maskedEmail = maskEmail(email);
		log.info("[AUTH_RESET_PASSWORD] 임시 비밀번호 발급 시작: email={}", maskedEmail);

		// 명세상 미가입 이메일은 404로 응답해야 하므로 UserException 던짐
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

		//임시 비밀번호 원문 생성
		String temporaryPassword = temporaryPasswordGenerator.generate();

		// DB에는 해시값 저장
		String temporaryPasswordHash = passwordEncoder.encode(temporaryPassword);

		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(TEMPORARY_PASSWORD_EXPIRATION_SECONDS);

		// 기존 임시 비밀번호가 있으면 갱신/ 없으면 새로 저장
		temporaryPasswordRepository.findByUser_Id(user.getId())
			.ifPresentOrElse(
				savedTemporaryPassword -> savedTemporaryPassword.reissue(
					temporaryPasswordHash,
					createdAt,
					expiresAt
				),
				() -> temporaryPasswordRepository.save(
					TemporaryPassword.builder()
						.user(user)
						.passwordHash(temporaryPasswordHash)
						.createdAt(createdAt)
						.expiresAt(expiresAt)
						.build()
				)
			);

		// 실제 메일 발송은 트랜잭션 커밋 이후 리스너에서 처리
		eventPublisher.publishEvent(new TemporaryPasswordIssuedEvent(
			user.getId(),
			user.getEmail(),
			temporaryPassword,
			expiresAt
		));

		log.info("[AUTH_RESET_PASSWORD] 임시 비밀번호 발급 이벤트 발행: userId={}, email={}", user.getId(), maskedEmail);
	}

	// 비밀번호 변경 완료 후 임시 비밀번호 삭제 시 사용
	@Transactional
	public void deleteTemporaryPassword(UUID userId) {
		temporaryPasswordRepository.deleteByUser_Id(userId);
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
