package com.team04.mopl.user.service.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 서버 시작 시 기본 어드민 계정 초기화
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${mopl.admin.enabled:true}")
	private boolean enabled;

	@Value("${mopl.admin.email:}")
	private String adminEmail;

	@Value("${mopl.admin.password:}")
	private String adminPassword;

	@Value("${mopl.admin.name:ADMIN}")
	private String adminName;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			log.info("[ADMIN_INIT] 어드민 계정 초기화 비활성화");

			return;
		}

		validateProperties();

		userRepository.findByEmail(adminEmail)
			.ifPresentOrElse(
				this::ensureAdminAccount,
				this::createAdminAccount
			);
	}

	private void ensureAdminAccount(User user) {
		boolean changed = false;

		// 기존 계정이 USER라면 ADMIN으로 보정
		if (!user.isAdmin()) {
			user.updateRole(UserRole.ADMIN);
			changed = true;
		}

		// 어드민 계정이 잠겨 있으면 초기화 시점에 잠금 해제
		if (user.isLocked()) {
			user.updateLocked(false);
			changed = true;
		}

		// 소셜 계정처럼 비밀번호가 없는 계정이면 설정된 어드민 비밀번호 지정
		if (!user.isPasswordLoginSupported()) {
			user.updatePasswordHash(passwordEncoder.encode(adminPassword));
			changed = true;
		}

		if (changed) {
			log.info(
				"[ADMIN_INIT] 기존 사용자를 어드민 계정으로 보정: userId={}, email={}",
				user.getId(),
				maskEmail(user.getEmail())
			);

			return;
		}

		log.info("[ADMIN_INIT] 어드민 계정이 이미 존재: email={}", maskEmail(user.getEmail()));
	}

	private void createAdminAccount() {
		User admin = User.builder()
			.name(adminName)
			.email(adminEmail)
			.emailType(EmailType.REAL)
			.passwordHash(passwordEncoder.encode(adminPassword))
			.role(UserRole.ADMIN)
			.locked(false)
			.build();

		userRepository.save(admin);

		log.info("[ADMIN_INIT] 어드민 계정 생성: email={}", maskEmail(adminEmail));
	}

	private void validateProperties() {
		if (!StringUtils.hasText(adminEmail)) {
			throw new IllegalStateException("어드민 이메일 설정이 없습니다.");
		}

		if (!StringUtils.hasText(adminPassword)) {
			throw new IllegalStateException("어드민 비밀번호 설정이 없습니다.");
		}

		if (!StringUtils.hasText(adminName)) {
			throw new IllegalStateException("어드민 이름 설정이 없습니다.");
		}
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
