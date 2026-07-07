package com.team04.mopl.playlist.event;

import java.time.Instant;
import java.util.UUID;

// 구독 중인 플레이리스트에 콘텐츠가 추가되면 해당 플레이리스트 구독자에게 발행하는 이벤트
public record PlaylistContentAddedEvent(

	UUID eventId,

	UUID playlistId,
	String playlistTitle,

	UUID playlistOwnerId,

	UUID contentId,
	String contentTitle,

	// 이벤트 발생 시간
	Instant occurredAt
) {
	public static PlaylistContentAddedEvent of(
		UUID playlistId,
		String playlistTitle,
		UUID playlistOwnerId,
		UUID playlistContentId,
		String playlistContentTitle
	) {
		return new PlaylistContentAddedEvent(
			UUID.randomUUID(),
			playlistId,
			playlistTitle,
			playlistOwnerId,
			playlistContentId,
			playlistContentTitle,
			Instant.now()
		);
	}
}
