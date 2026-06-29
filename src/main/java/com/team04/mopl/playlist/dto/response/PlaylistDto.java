package com.team04.mopl.playlist.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;

public record PlaylistDto(

	UUID id,
	UserSummary owner,
	String title,
	String description,
	Instant updatedAt,
	Long subscriberCount,
	Boolean subscribedByMe,
	List<ContentSummary> contents
) {
}
