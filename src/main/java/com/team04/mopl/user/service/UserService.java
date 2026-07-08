package com.team04.mopl.user.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.storage.FileStorage;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserUpdateRequest;
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

	// 프로필 사진이 저장될 디렉토리
	private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final FileStorage fileStorage;

	// 회원가입
	@Transactional
	public UserDto create(UserCreateRequest request) {
		String maskedEmail = maskEmail(request.email());
		log.info("[USER_CREATE] 회원가입 처리 시작: email={}", maskedEmail);

		if (userRepository.existsByEmail(request.email())) {
			log.warn("[USER_CREATE] 회원가입 실패 - 이미 사용 중인 이메일: email={}", maskedEmail);

			throw new UserException(
				UserErrorCode.USER_EMAIL_ALREADY_EXISTS,
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
			// 동시 중복 가입 방어 -> users.email unique 제약 위반 시 예외 발생
			User savedUser = userRepository.saveAndFlush(user);
			log.info("[USER_CREATE] 회원가입 성공: userId={}, email={}", savedUser.getId(), maskedEmail);

			return userMapper.toDto(savedUser);
		} catch (DataIntegrityViolationException exception) {
			log.warn("[USER_CREATE] 회원가입 실패 - DB unique 제약 위반: email={}", maskedEmail);

			// 이미 사용 중인 이메일 예외
			throw new UserException(
				UserErrorCode.USER_EMAIL_ALREADY_EXISTS,
				Map.of("email", request.email())
			);
		}
	}

	// 프로필 변경
	@Transactional
	public UserDto updateProfile(
		UUID userId,
		UserUpdateRequest request,
		MultipartFile image,
		UUID currentUserId
	) {
		log.info("[USER_PROFILE_UPDATE] 프로필 변경 시작: userId={}, currentUserId={}, imagePresent={}",
			userId, currentUserId, hasImage(image));

		validateProfileOwner(userId, currentUserId);

		User user = getUserOrThrow(userId);
		String oldProfileImageUrl = user.getProfileImageUrl();
		String newProfileImageUrl = null;

		if (hasImage(image)) {
			newProfileImageUrl = fileStorage.store(image, PROFILE_IMAGE_DIRECTORY);
		}

		try {
			updateNameIfPresent(user, request.name());
			updateProfileImageIfPresent(user, newProfileImageUrl);

			UserDto userDto = userMapper.toDto(user);

			deleteOldProfileImageIfChanged(oldProfileImageUrl, newProfileImageUrl);

			log.info("[USER_PROFILE_UPDATE] 프로필 변경 완료: userId={}, imageChanged={}",
				userId, newProfileImageUrl != null);

			return userDto;
		} catch (Exception exception) {
			deleteNewProfileImageAfterFailure(newProfileImageUrl);

			throw exception;
		}
	}

	// 프로필 변경 대상 본인 여부 검증
	private void validateProfileOwner(UUID userId, UUID currentUserId) {
		if (!userId.equals(currentUserId)) {
			throw new UserException(
				UserErrorCode.USER_PROFILE_ACCESS_DENIED,
				Map.of("userId", userId, "currentUserId", String.valueOf(currentUserId))
			);
		}
	}

	// 사용자 조회
	private User getUserOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			));
	}

	// 프로필 이미지 요청 여부 확인
	private boolean hasImage(MultipartFile image) {
		return image != null && !image.isEmpty();
	}

	// 이름 요청값이 있을 때만 변경
	private void updateNameIfPresent(User user, String name) {
		if (name != null) {
			user.updateName(name);
		}
	}

	// 새 프로필 이미지가 있을 때만 변경
	private void updateProfileImageIfPresent(User user, String newProfileImageUrl) {
		if (newProfileImageUrl != null) {
			user.updateProfileImageUrl(newProfileImageUrl);
		}
	}

	// 프로필 이미지 교체 완료 후 기존 이미지 삭제
	private void deleteOldProfileImageIfChanged(String oldProfileImageUrl, String newProfileImageUrl) {
		if (newProfileImageUrl == null
			|| oldProfileImageUrl == null
			|| oldProfileImageUrl.isBlank()
			|| oldProfileImageUrl.equals(newProfileImageUrl)) {
			return;
		}

		try {
			fileStorage.delete(oldProfileImageUrl);
		} catch (Exception exception) {
			log.error("[USER_PROFILE_UPDATE] 기존 프로필 이미지 삭제 실패, 배치 정리 필요: profileImageUrl={}",
				oldProfileImageUrl, exception);
		}
	}

	// 프로필 변경 실패 시 새 이미지 보상 삭제
	private void deleteNewProfileImageAfterFailure(String newProfileImageUrl) {
		if (newProfileImageUrl == null) {
			return;
		}

		try {
			fileStorage.delete(newProfileImageUrl);
		} catch (Exception exception) {
			log.error("[USER_PROFILE_UPDATE] 새 프로필 이미지 삭제 실패, 배치 정리 필요: profileImageUrl={}",
				newProfileImageUrl, exception);
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
