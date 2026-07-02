package com.team04.mopl.playlist.event;

import java.time.Instant;
import java.util.UUID;

// 다른 사용자가 플레이리스트를 구독 완료 후 플레이리스트 소유자에게 발행하는 이벤트
public record PlaylistSubscribedEvent(

	UUID eventId,

	UUID playlistId,
	String playlistTitle,

	UUID playlistOwnerId,

	UUID subscriberUserId,
	String subscriberName,

	// 이벤트 발생 시간
	Instant occurredAt
) {
	public static PlaylistSubscribedEvent of(
		UUID playlistId,
		String playlistTitle,
		UUID playlistOwnerId,
		UUID subscriberUserId,
		String subscriberName
	) {
		return new PlaylistSubscribedEvent(
			UUID.randomUUID(),
			playlistId,
			playlistTitle,
			playlistOwnerId,
			subscriberUserId,
			subscriberName,
			Instant.now()
		);
	}
}
