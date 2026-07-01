package com.team04.mopl.notification.dto.response;

import java.util.List;

import com.team04.mopl.notification.entity.Notification;

public record NotificationCursorPage(

	List<Notification> notificationList,
	boolean hasNext,
	long totalCount
) {

	public NotificationCursorPage {
		notificationList = (notificationList == null)
			? List.of()
			: List.copyOf(notificationList);
	}
}
