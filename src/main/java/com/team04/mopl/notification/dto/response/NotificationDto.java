package com.team04.mopl.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.notification.enums.NotificationLevel;

public record NotificationDto(

	// 알림 id
	UUID id,

	// 알림 생성 시간
	Instant createdAt,

	// 수신자 id
	UUID receiverId,

	// 알림 제목
	String title,

	// 알림 내용
	String content,

	// 알림 수준
	NotificationLevel level
) {
}
