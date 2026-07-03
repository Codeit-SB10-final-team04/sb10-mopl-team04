package com.team04.mopl.watching.dto;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;

public record WatchingSessionDto(
	UUID id,
	Instant createdAt,
	UserSummary watcher,
	ContentSummary content
) {
}
