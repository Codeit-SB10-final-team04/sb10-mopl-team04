package com.team04.mopl.watching.dto;

import com.team04.mopl.watching.enums.ChangeType;

public record WatchingSessionChange(
	ChangeType type,

) {
}
