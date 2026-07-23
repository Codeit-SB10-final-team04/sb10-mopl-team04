package com.team04.mopl.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.notification.enums.NotificationLevel;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationDto(

	@Schema(description = "알림 ID")
	UUID id,

	@Schema(description = "알림 생성 시간")
	Instant createdAt,

	@Schema(description = "수신자 ID")
	UUID receiverId,

	@Schema(description = "알림 제목")
	String title,

	@Schema(description = "알림 내용")
	String content,

	@Schema(description = "알림 수준")
	NotificationLevel level
) {
}
