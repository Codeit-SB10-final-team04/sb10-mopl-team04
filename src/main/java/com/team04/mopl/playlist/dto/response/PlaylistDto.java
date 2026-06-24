package com.team04.mopl.playlist.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.user.dto.response.UserSummary;

public record PlaylistDto(

	UUID id,
	UserSummary owner,
	String title,
	String description,
	Instant updatedAt,
	long subscriberCount,
	boolean subscribedByMe,
	List<String> contents
	// TODO: ContentSummary 구현 후 변경
	// List<ContentSummary> contents
) {
}
