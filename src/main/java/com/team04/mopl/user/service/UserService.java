package com.team04.mopl.user.service;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.mapper.UserMapper;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	// 회원가입
	@Transactional
	public UserDto create(UserCreateRequest request) {
		String maskedEmail = maskEmail(request.email());
		log.info("[USER_CREATE] 회원가입 처리 시작: email={}", maskedEmail);

		if (userRepository.existsByEmail(request.email())) {
			log.warn("[USER_CREATE] 회원가입 실패 - 이미 사용 중인 이메일: email={}", maskedEmail);

			throw new UserException(
				UserErrorCode.EMAIL_ALREADY_EXISTS,
				Map.of("email", request.email())
			);
		}

		String passwordHash = passwordEncoder.encode(request.password());

		User user = User.builder()
			.name(request.name())
			.email(request.email())
			.passwordHash(passwordHash)
			.build();

		try {
			// 동시 중복 가입 방어 -> users.email unique 제약 위발 시 예외 발생
			User savedUser = userRepository.saveAndFlush(user);
			log.info("[USER_CREATE] 회원가입 성공: userId={}, email={}", savedUser.getId(), maskedEmail);

			return userMapper.toDto(savedUser);
		} catch (DataIntegrityViolationException exception) {
			log.warn("[USER_CREATE] 회원가입 실패 - DB unique 제약 위반: email={}", maskedEmail);

			// 이미 사용 중인 이메일 예외
			throw new UserException(
				UserErrorCode.EMAIL_ALREADY_EXISTS,
				exception,
				Map.of("email", request.email())
			);
		}
	}

	// 로그 이메일 마스킹 처리용
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
