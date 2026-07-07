package com.team04.mopl.watching.dto.response;

import com.team04.mopl.watching.enums.ChangeType;

public record WatchingSessionChange(
	ChangeType type,
	WatchingSessionDto watchingSession,
	long watcherCount
) {
}
