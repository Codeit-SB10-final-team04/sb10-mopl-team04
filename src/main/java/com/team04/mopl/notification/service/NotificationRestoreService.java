package com.team04.mopl.notification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.mapper.NotificationMapper;
import com.team04.mopl.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRestoreService {

	private static final int RECOVERY_LIMIT = 500;
	private static final int RECOVERY_MINUTES = 10;
	private final NotificationRepository notificationRepository;
	private final NotificationMapper notificationMapper;

	@Transactional(readOnly = true)
	public List<NotificationDto> findUnreadNotificationsAfter(
		UUID receiverId,
		UUID lastEventId
	) {
		log.debug("[NOTIFICATION_RESTORE_FIND_UNREAD_AFTER] 미읽음 알림 복구 조회 시작: receiverId={}, lastEventId={}, limit={}",
			receiverId, lastEventId, RECOVERY_LIMIT);

		// lastEventId가 receiverId 소유인지 확인
		Notification lastNotification =
			notificationRepository.findByIdAndReceiverId(lastEventId, receiverId)
				.orElse(null);

		PageRequest pageRequest = PageRequest.of(0, RECOVERY_LIMIT);
		Instant timeLimit = Instant.now().minus(RECOVERY_MINUTES, ChronoUnit.MINUTES);

		List<Notification> notifications = lastNotification == null
			? notificationRepository.findRecentUnreadNotifications(receiverId, timeLimit, pageRequest)
			: notificationRepository.findUnreadNotificationsAfter(receiverId, lastNotification.getId(),
			lastNotification.getCreatedAt(),
			pageRequest);

		// notificationDto로 변환
		List<NotificationDto> restoredNotifications = notifications.stream()
			.map(notificationMapper::toDto)
			.toList();

		log.debug(
			"[NOTIFICATION_RESTORE_FIND_UNREAD_AFTER] 미읽음 알림 복구 조회 완료: receiverId={}, lastEventId={}, restoredCount={}",
			receiverId, lastEventId, restoredNotifications.size());

		return restoredNotifications;
	}
}
