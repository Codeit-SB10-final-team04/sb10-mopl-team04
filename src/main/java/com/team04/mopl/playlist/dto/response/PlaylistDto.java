package com.team04.mopl.playlist.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDto(

	UUID id,
	// TODO: UserSummary 구현 후 변경
	// UserSummary owner,
	PlaylistUserSummary owner,
	String title,
	String description,
	Instant updatedAt,
	long subscriberCount,
	boolean subscribedByMe,
	// TODO: ContentSummary 구현 후 변경
	// List<ContentSummary> contents
	List<PlaylistContentSummary> contents
) {
}
