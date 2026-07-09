package com.team04.mopl.follow.event;

import java.time.Instant;
import java.util.UUID;

// 특정 사용자를 팔로우 하면 해당 팔로우를 당한 사용자에게 발행하는 이벤트
public record FollowCreatedEvent(

	UUID eventId,

	UUID followeeId,
	String followeeName,

	UUID followerId,
	String followerName,

	// 이벤트 발행 시간
	Instant occurredAt
) {
	public static FollowCreatedEvent of(
		UUID followeeId,
		String followeeName,
		UUID followerId,
		String followerName
	) {
		return new FollowCreatedEvent(
			UUID.randomUUID(),
			followeeId,
			followeeName,
			followerId,
			followerName,
			Instant.now()
		);
	}
}
