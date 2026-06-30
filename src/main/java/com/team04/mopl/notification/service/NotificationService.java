package com.team04.mopl.notification.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.exception.NotificationErrorCode;
import com.team04.mopl.notification.exception.NotificationException;
import com.team04.mopl.notification.mapper.NotificationMapper;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

	private final UserRepository userRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationMapper notificationMapper;

	// TODO: 도메인 이벤트 Listener를 AFTER_COMMIT으로 확정한 뒤 @Transactional 삭제 및 private 메서드로 변경 검토
	// TODO: 아마 private 메서드가 되지 않을까?
	@Transactional
	public NotificationDto createNotification(
		UUID receiverId,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		log.debug("[NOTIFICATION_CREATE] 알림 생성 시작: title={}, content={}, type={}, level={}",
			title, content, type, level);

		// input null 및 blank 검증
		validateInputNullOrBlank(receiverId, title, content, type, level);

		// 길이 검증
		validateInputSize(title);

		User receiver = getUserOrThrow(receiverId);

		Notification notification = Notification.builder()
			.receiver(receiver)
			.title(title)
			.content(content)
			.type(type)
			.level(level)
			.build();

		// 알림 저장
		notificationRepository.save(notification);

		log.debug("[NOTIFICATION_CREATE] 알림 생성 완료: title={}, content={}, type={}, level={}",
			title, content, type, level);

		// 알림 저장 후 dto로 변환
		return notificationMapper.toDto(notification);
	}

	// TODO: 도메인 이벤트 리스너를 AFTER_COMMIT으로 확정한 뒤 REQUIRES_NEW 재검토
	// @Transactional(propagation = Propagation.REQUIRES_NEW)
	@Transactional
	public List<NotificationDto> createNotificationList(
		Set<UUID> receiverIds,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		if (receiverIds == null) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("isReceiverProvided", false);
		}

		if (receiverIds.isEmpty()) {
			return List.of();
		}

		log.debug("[NOTIFICATION_LIST_CREATE] 알림 생성 시작: receiverCount={}, title={}, content={}, type={}, level={}",
			receiverIds.size(), title, content, type, level);

		List<NotificationDto> notificationDtoList = receiverIds
			.stream()
			.map(receiverId -> createNotification(receiverId, title, content, type, level))
			.toList();

		log.debug("[NOTIFICATION_LIST_CREATE] 알림 생성 완료: receiverCount={}, notificationCount={}",
			receiverIds.size(), notificationDtoList.size());

		return notificationDtoList;
	}

	// 사용자 조회
	private User getUserOrThrow(UUID userId) {
		return userRepository.findByIdAndLockedFalse(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// input null or blank 검증
	private void validateInputNullOrBlank(
		UUID receiverId,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		boolean titleBlank = title != null && title.isBlank();
		boolean contentBlank = content != null && content.isBlank();

		if (receiverId == null || title == null || content == null || type == null || level == null
			|| titleBlank || contentBlank
		) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("isReceiverIdProvided", receiverId != null)
				.addDetail("isTitleProvided", title != null)
				.addDetail("isTitleBlank", titleBlank)
				.addDetail("isContentProvided", content != null)
				.addDetail("isContentBlank", contentBlank)
				.addDetail("isTypeProvided", type != null)
				.addDetail("isLevelProvided", level != null);
		}
	}

	// input 길이 검증
	private void validateInputSize(String title) {
		if (title.length() > 50) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("titleLength", title.length());
		}
	}
}
