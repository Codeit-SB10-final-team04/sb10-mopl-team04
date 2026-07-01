package com.team04.mopl.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.entity.TemporaryPassword;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
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

	private static final long TEMPORARY_PASSWORD_EXPIRATION_SECONDS = 180L;

	private final UserRepository userRepository;
	private final TemporaryPasswordRepository temporaryPasswordRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final TemporaryPasswordMailSender temporaryPasswordMailSender;

	// 이메일 주소로 임시 비밀번호를 발급하고 이메일로 전송
	@Transactional
	public void resetPassword(String email) {
		String maskedEmail = maskEmail(email);
		log.info("[AUTH_RESET_PASSWORD] 임시 비밀번호 발급 시작: email={}", maskedEmail);

		// 요청 이메일에 해당하는 사용자 조회
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("email", email)
			));

		// 사용자에게 전달할 임시 비밀번호 원문 생성
		String temporaryPassword = temporaryPasswordGenerator.generate();
		String temporaryPasswordHash = passwordEncoder.encode(temporaryPassword);

		// 임시 비밀번호는 3분 동안만 유효
		Instant createdAt = Instant.now();
		Instant expiresAt = createdAt.plusSeconds(TEMPORARY_PASSWORD_EXPIRATION_SECONDS);

		TemporaryPassword temporaryPasswordEntity = TemporaryPassword.builder()
			.user(user)
			.passwordHash(temporaryPasswordHash)
			.createdAt(createdAt)
			.expiresAt(expiresAt)
			.build();

		// 이미 발급된 임시 비밀번호가 있으면 갱신, 없으면 새로 저장
		temporaryPasswordRepository.findById(user.getId())
			.ifPresentOrElse(
				savedTemporaryPassword -> savedTemporaryPassword.reissue(
					temporaryPasswordHash,
					createdAt,
					expiresAt
				),
				() -> temporaryPasswordRepository.save(temporaryPasswordEntity)
			);

		// 사용자가 입력한 이메일로 임시 비밀번호 원문과 만료 시각 전송
		temporaryPasswordMailSender.sendTemporaryPassword(email, temporaryPassword, expiresAt);

		log.info("[AUTH_RESET_PASSWORD] 임시 비밀번호 발급 성공: userId={}, email={}", user.getId(), maskedEmail);
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
