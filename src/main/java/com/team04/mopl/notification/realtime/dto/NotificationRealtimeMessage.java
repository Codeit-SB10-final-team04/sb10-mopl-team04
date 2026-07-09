package com.team04.mopl.notification.realtime.dto;

import java.util.UUID;

import com.team04.mopl.notification.dto.response.NotificationDto;

// Redis 메시지
public record NotificationRealtimeMessage(

	UUID receiverId,
	UUID eventId,
	String eventName,
	NotificationDto data
) {
}
