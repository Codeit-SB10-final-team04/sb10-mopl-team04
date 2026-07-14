package com.team04.mopl.user.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.auth.service.TemporaryPasswordService;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.request.ChangePasswordRequest;
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

	// 프로필 이미지 저장 디렉토리
	static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

	// 프로필 이미지 최대 크기
	private static final long MAX_PROFILE_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;

	// 프로필 이미지 허용 Content-Type 목록
	private static final Set<String> ALLOWED_PROFILE_IMAGE_CONTENT_TYPES = Set.of(
		"image/jpeg",
		"image/png",
		"image/gif",
		"image/webp"
	);

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final FileStorage fileStorage;
	private final TemporaryPasswordService temporaryPasswordService;
	private final AuthSessionStore authSessionStore;

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
			// 동시 중복 가입 방어
			User savedUser = userRepository.saveAndFlush(user);
			log.info("[USER_CREATE] 회원가입 성공: userId={}, email={}", savedUser.getId(), maskedEmail);

			return userMapper.toDto(savedUser);
		} catch (DataIntegrityViolationException exception) {
			log.warn("[USER_CREATE] 회원가입 실패 - DB unique 제약 위반: email={}", maskedEmail);

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

		validateOwner(userId, currentUserId, UserErrorCode.USER_PROFILE_ACCESS_DENIED);

		User user = getUserOrThrow(userId);
		updateNameIfPresent(user, request.name());

		String oldProfileImageUrl = user.getProfileImageUrl();
		String newProfileImageUrl = null;

		if (hasImage(image)) {
			validateProfileImage(image);
			newProfileImageUrl = fileStorage.store(image, PROFILE_IMAGE_DIRECTORY);
		}

		try {
			updateProfileImageIfPresent(user, newProfileImageUrl);

			UserDto userDto = userMapper.toDto(user);

			registerOldProfileImageDeletionAfterCommit(oldProfileImageUrl, newProfileImageUrl);

			log.info("[USER_PROFILE_UPDATE] 프로필 변경 완료: userId={}, imageChanged={}",
				userId, newProfileImageUrl != null);

			return userDto;
		} catch (Exception exception) {
			deleteNewProfileImageAfterFailure(newProfileImageUrl);

			throw exception;
		}
	}

	// 비밀번호 변경
	@Transactional
	public void updatePassword(UUID userId, ChangePasswordRequest request, UUID currentUserId) {
		log.info("[USER_PASSWORD_UPDATE] 비밀번호 변경 시작: userId={}, currentUserId={}",
			userId, currentUserId);

		validateOwner(userId, currentUserId, UserErrorCode.USER_PASSWORD_ACCESS_DENIED);

		User user = getUserOrThrow(userId);
		String passwordHash = passwordEncoder.encode(request.password());

		user.updatePasswordHash(passwordHash);
		temporaryPasswordService.deleteTemporaryPassword(userId);

		// 비밀번호 변경 이후 인증 세션 무효화
		authSessionStore.deleteByUserId(userId);

		log.info("[USER_PASSWORD_UPDATE] 비밀번호 변경 및 임시 비밀번호 삭제 완료: userId={}", userId);
	}

	// 사용자 상세 조회
	public UserDto findById(UUID userId) {
		log.debug("[USER_FIND_BY_ID] 사용자 상세 조회 시작: userId={}", userId);

		User user = getUserOrThrow(userId);
		UserDto userDto = userMapper.toDto(user);

		log.debug("[USER_FIND_BY_ID] 사용자 상세 조회 완료: userId={}", userId);

		return userDto;
	}

	// 요청 대상 본인 여부 검증
	private void validateOwner(UUID userId, UUID currentUserId, UserErrorCode errorCode) {
		if (!userId.equals(currentUserId)) {
			throw new UserException(
				errorCode,
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

	// 프로필 이미지 업로드 조건 검증
	private void validateProfileImage(MultipartFile image) {
		validateProfileImageSize(image);
		validateProfileImageContentType(image);
		// Content-Type은 클라이언트가 조작할 수 있으므로 실제 이미지로 디코딩 가능한지도 검증한다.
		validateProfileImageReadable(image);
	}

	// 프로필 이미지 크기 검증
	private void validateProfileImageSize(MultipartFile image) {
		if (image.getSize() <= MAX_PROFILE_IMAGE_SIZE_BYTES) {
			return;
		}

		throw new UserException(
			UserErrorCode.USER_INVALID_PROFILE_IMAGE,
			Map.of(
				"maxSizeBytes", MAX_PROFILE_IMAGE_SIZE_BYTES,
				"sizeBytes", image.getSize()
			)
		);
	}

	// 프로필 이미지 Content-Type 검증
	private void validateProfileImageContentType(MultipartFile image) {
		String contentType = image.getContentType();
		String normalizedContentType = contentType == null ? null : contentType.toLowerCase(Locale.ROOT);

		if (normalizedContentType != null && ALLOWED_PROFILE_IMAGE_CONTENT_TYPES.contains(normalizedContentType)) {
			return;
		}

		throw new UserException(
			UserErrorCode.USER_INVALID_PROFILE_IMAGE,
			Map.of(
				"contentType", String.valueOf(contentType),
				"allowedContentTypes", ALLOWED_PROFILE_IMAGE_CONTENT_TYPES
			)
		);
	}

	// ImageIO가 실제 이미지 바이트를 해석하지 못하면 확장자/Content-Type이 맞아도 거부
	private void validateProfileImageReadable(MultipartFile image) {
		try (InputStream inputStream = image.getInputStream()) {
			BufferedImage bufferedImage = ImageIO.read(inputStream);

			if (bufferedImage != null && bufferedImage.getWidth() > 0 && bufferedImage.getHeight() > 0) {
				return;
			}
		} catch (IOException exception) {
			throw invalidProfileImage("이미지 파일을 읽을 수 없습니다.");
		}

		throw invalidProfileImage("지원하지 않거나 손상된 이미지 파일입니다.");
	}

	// 프로필 이미지 실패 예외
	private UserException invalidProfileImage(String reason) {
		return new UserException(
			UserErrorCode.USER_INVALID_PROFILE_IMAGE,
			Map.of("reason", reason)
		);
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

	// 트랜잭션 커밋 이후 기존 프로필 이미지 삭제 예약
	private void registerOldProfileImageDeletionAfterCommit(String oldProfileImageUrl, String newProfileImageUrl) {
		if (!shouldDeleteOldProfileImage(oldProfileImageUrl, newProfileImageUrl)) {
			return;
		}

		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			deleteOldProfileImageIfChanged(oldProfileImageUrl, newProfileImageUrl);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				deleteOldProfileImageIfChanged(oldProfileImageUrl, newProfileImageUrl);
			}
		});
	}

	// 기존 프로필 이미지 삭제 필요 여부 확인
	private boolean shouldDeleteOldProfileImage(String oldProfileImageUrl, String newProfileImageUrl) {
		return newProfileImageUrl != null
			&& oldProfileImageUrl != null
			&& !oldProfileImageUrl.isBlank()
			&& !oldProfileImageUrl.equals(newProfileImageUrl);
	}

	// 기존 프로필 이미지 삭제
	private void deleteOldProfileImageIfChanged(String oldProfileImageUrl, String newProfileImageUrl) {
		if (!shouldDeleteOldProfileImage(oldProfileImageUrl, newProfileImageUrl)) {
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
