package com.team04.mopl.notification.repository.qdsl;

import java.util.UUID;

import com.team04.mopl.notification.dto.request.NotificationPageRequest;
import com.team04.mopl.notification.dto.response.NotificationCursorPage;

public interface NotificationQdslRepository {

	NotificationCursorPage findAllNotifications(
		NotificationPageRequest request,
		UUID currentUserId
	);
}
