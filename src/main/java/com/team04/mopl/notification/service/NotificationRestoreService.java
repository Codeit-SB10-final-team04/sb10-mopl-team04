package com.team04.mopl.notification.service;

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

	private final NotificationRepository notificationRepository;
	private final NotificationMapper notificationMapper;

	private static final int RECOVERY_LIMIT = 500;

	@Transactional(readOnly = true)
	public List<NotificationDto> findUnreadNotificationsAfter(
		UUID receiverId,
		UUID lastEventId
	) {
		// lastEventId가 receiverId 소유인지 확인
		Notification lastNotification =
			notificationRepository.findByIdAndReceiverId(lastEventId, receiverId)
				.orElse(null);

		// 알림이 없을 경우 빈 리스트 반환
		if (lastNotification == null) {
			log.warn("[SSE_LAST_EVENT_NOT_FOUND] lastEventId 기준 알림을 찾을 수 없음: receiverId={}, lastEventId={}",
				receiverId, lastEventId);

			return List.of();
		}

		// lastEvent의 createdAt과 id를 기준으로 미읽음 알림 조회
		List<Notification> afterNotificationList =
			notificationRepository.findUnreadNotificationsAfter(
				receiverId,
				lastNotification.getId(),
				lastNotification.getCreatedAt(),
				PageRequest.of(0, RECOVERY_LIMIT)
			);

		// notificationDto로 변환 후 반환
		return afterNotificationList.stream()
			.map(notification -> notificationMapper.toDto(notification))
			.toList();
	}
}
