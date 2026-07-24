package com.team04.mopl.follow.event;

import java.util.UUID;

public record FollowDeletedEvent(
	UUID followeeId,
	UUID followerId
) {
}
