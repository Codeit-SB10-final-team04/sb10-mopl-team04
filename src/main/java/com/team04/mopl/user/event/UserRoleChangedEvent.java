package com.team04.mopl.user.event;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.user.entity.UserRole;

// 사용자 권한 변경 시 해당 사용자에게 발행하는 이벤트
public record UserRoleChangedEvent(

	UUID eventId,

	UUID userId,
	UserRole previousRole,
	UserRole newRole,

	// 이벤트 발생 시간
	Instant occurredAt
) {
	public static UserRoleChangedEvent of(
		UUID userId,
		UserRole previousRole,
		UserRole newRole
	) {
		return new UserRoleChangedEvent(
			UUID.randomUUID(),
			userId,
			previousRole,
			newRole,
			Instant.now()
		);
	}
}
