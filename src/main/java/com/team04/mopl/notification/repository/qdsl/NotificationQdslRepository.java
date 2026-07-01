package com.team04.mopl.notification.repository.qdsl;

import java.util.UUID;

import com.team04.mopl.notification.dto.request.NotificationSearchRequest;
import com.team04.mopl.notification.dto.response.NotificationCursorPageDto;

public interface NotificationQdslRepository {

	NotificationCursorPageDto findAllNotifications(
		NotificationSearchRequest request,
		UUID currentUserId
	);
}
