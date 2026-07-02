package com.team04.mopl.playlist.event;

import java.time.Instant;
import java.util.UUID;

// 특정 사용자가 플레이리스트를 생성하면 해당 사용자의 팔로워에게 발행하는 이벤트
public record PlaylistCreatedEvent(

	UUID eventId,

	UUID playlistId,
	String playlistTitle,

	UUID playlistOwnerId,
	String playlistOwnerName,

	// 이벤트 발생 시간
	Instant occurredAt

) {
	public static PlaylistCreatedEvent of(
		UUID playlistId,
		String playlistTitle,
		UUID playlistOwnerId,
		String playlistOwnerName
	) {
		return new PlaylistCreatedEvent(
			UUID.randomUUID(),
			playlistId,
			playlistTitle,
			playlistOwnerId,
			playlistOwnerName,
			Instant.now()
		);
	}
}

