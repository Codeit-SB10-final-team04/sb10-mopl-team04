package com.team04.mopl.notification.service;

import static java.util.stream.Collectors.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.notification.dto.request.NotificationSearchRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;
import com.team04.mopl.notification.dto.response.NotificationCursorPage;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationSortBy;
import com.team04.mopl.notification.enums.NotificationType;
import com.team04.mopl.notification.exception.NotificationErrorCode;
import com.team04.mopl.notification.exception.NotificationException;
import com.team04.mopl.notification.mapper.NotificationMapper;
import com.team04.mopl.notification.repository.NotificationRepository;
import com.team04.mopl.user.entity.User;
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
	// TODO: private 메서드로 변경될 경우 로그 삭제
	// @Transactional(propagation = Propagation.REQUIRES_NEW)
	@Transactional
	public List<NotificationDto> saveNotificationList(
		Set<UUID> receiverIds,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		validateCreateNotificationListRequest(receiverIds, title, content, type, level);

		// receiverIds Set이 비었으면 빈 리스트 반환
		if (receiverIds.isEmpty()) {
			return List.of();
		}

		log.info("[NOTIFICATION_LIST_CREATE] 알림 생성 시작: receiverCount={}, type={}, level={}",
			receiverIds.size(), type, level);

		// 수신인 Map 조회 및 누락 검증
		Map<UUID, User> receiverMap = getReceiverMapOrThrow(receiverIds);

		List<Notification> notificationList = saveNotificationList(
			receiverIds,
			receiverMap,
			title,
			content,
			type,
			level
		);

		// 알림 저장 및 dto로 변환
		List<NotificationDto> notificationDtoList = notificationRepository.saveAll(notificationList)
			.stream()
			.map(notification -> notificationMapper.toDto(notification))
			.toList();

		log.info("[NOTIFICATION_LIST_CREATE] 알림 생성 완료: receiverCount={}, notificationCount={}",
			receiverIds.size(), notificationDtoList.size());

		return notificationDtoList;
	}

	@Transactional(readOnly = true)
	public CursorResponseNotificationDto findAllNotifications(
		NotificationSearchRequest request,
		UUID currentUserId
	) {
		int limit = request.limit();
		SortDirection sortDirection = request.sortDirection();
		NotificationSortBy sortBy = request.sortBy();

		log.debug("[NOTIFICATION_FIND_ALL] 알림 목록 조회 시작: cursor={}, idAfter={}, limit={}, sortDirection={}, sortBy={}",
			request.cursor(), request.idAfter(), limit, sortDirection, sortBy);

		// 조건에 따라 알림 목록 조회
		NotificationCursorPage notificationCursorPage =
			notificationRepository.findAllNotifications(request, currentUserId);

		// notificationDto 조립
		List<NotificationDto> notificationDtoList = notificationCursorPage.notificationList().stream()
			.map(notification -> notificationMapper.toDto(notification))
			.toList();

		int notificationListSize = notificationDtoList.size();
		boolean hasNext = notificationCursorPage.hasNext();

		// 마지막 요소
		NotificationDto lastNotificationDto = notificationDtoList.isEmpty()
			? null
			: notificationDtoList.get(notificationListSize - 1);
		// 다음 cursor
		String nextCursor = hasNext && lastNotificationDto != null
			? lastNotificationDto.createdAt().toString()
			: null;
		// 다음 보조 cursor (idAfter)
		UUID nextIdAfter = hasNext && lastNotificationDto != null
			? lastNotificationDto.id()
			: null;

		// Cursor 페이지네이션 만들기
		CursorResponseNotificationDto cursorResponseNotificationDto
			= new CursorResponseNotificationDto(
			notificationDtoList,
			nextCursor,
			nextIdAfter,
			hasNext,
			notificationCursorPage.totalCount(),
			sortBy.toString(),
			sortDirection
		);

		log.debug("[NOTIFICATION_FIND_ALL] 알림 목록 조회 완료: size={}, nextCursor={}, nextIdAfter={}",
			notificationListSize, nextCursor, nextIdAfter);

		return cursorResponseNotificationDto;
	}

	@Transactional
	public void readNotification(UUID notificationId, UUID currentUserId) {
		log.info("[NOTIFICATION_READ] 알림 읽기 시작: notificationId={}, currentUserId={}",
			notificationId, currentUserId);

		// 알림 조회
		Notification notification = getNotificationOrThrow(notificationId, currentUserId);

		// readAt 갱신
		notification.markRead(Instant.now());

		log.info("[NOTIFICATION_READ] 알림 읽기 완료: notificationId={}, currentUserId={}",
			notificationId, currentUserId);
	}

	// 수신인 Map 조회 및 누락 검증
	private Map<UUID, User> getReceiverMapOrThrow(Set<UUID> receiverIds) {
		Map<UUID, User> receiverMap = userRepository.findAllByIdInAndLockedFalse(receiverIds).stream()
			.collect(toMap(
					user -> user.getId(),
					user -> user
				)
			);

		// 누락 검증
		if (receiverMap.size() != receiverIds.size()) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_RECEIVER_NOT_FOUND)
				.addDetail("receiverIdsSize", receiverIds.size())
				.addDetail("receiverMapSize", receiverMap.size());
		}

		return receiverMap;
	}

	// 알림 id와 현재 사용자 id로 알림 조회
	private Notification getNotificationOrThrow(UUID notificationId, UUID currentUserId) {
		// 멱등성 보장을 위해 `readAt IS NULL` X
		return notificationRepository.findByIdAndReceiverId(notificationId, currentUserId)
			.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
	}

	private List<Notification> saveNotificationList(
		Set<UUID> receiverIds,
		Map<UUID, User> receiverMap,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level

	) {
		return receiverIds.stream()
			.map(receiverId -> createNotification(
				receiverMap.get(receiverId),
				title,
				content,
				type,
				level
			))
			.toList();
	}

	private Notification createNotification(
		User receiver,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		return Notification.builder()
			.receiver(receiver)
			.title(title)
			.content(content)
			.type(type)
			.level(level)
			.build();
	}

	// ========== validate ==========
	// 검증 통합
	private void validateCreateNotificationListRequest(
		Set<UUID> receiverIds,
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		// 입력된 알림 정보 null or blank 검증
		validateNotificationInfoNullOrBlank(title, content, type, level);

		// 길이 검증
		validateTitleSize(title);

		// 입력된 receiverId null or blank 검증
		validateReceiverIdsNullOrBlank(receiverIds);
	}

	// 입력된 알림 정보 null or blank 검증
	private void validateNotificationInfoNullOrBlank(
		String title,
		String content,
		NotificationType type,
		NotificationLevel level
	) {
		boolean titleBlank = title != null && title.isBlank();
		boolean contentBlank = content != null && content.isBlank();

		if (title == null || content == null || type == null || level == null
			|| titleBlank || contentBlank
		) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("isTitleProvided", title != null)
				.addDetail("isTitleBlank", titleBlank)
				.addDetail("isContentProvided", content != null)
				.addDetail("isContentBlank", contentBlank)
				.addDetail("isTypeProvided", type != null)
				.addDetail("isLevelProvided", level != null);
		}
	}

	// 입력된 title 길이 검증
	private void validateTitleSize(String title) {
		if (title.length() > 50) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("titleLength", title.length());
		}
	}

	// 입력된 receiverId null or blank 검증
	private void validateReceiverIdsNullOrBlank(Set<UUID> receiverIds) {
		if (receiverIds == null) {
			throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
				.addDetail("isReceiverProvided", false);
		}
	}
}
