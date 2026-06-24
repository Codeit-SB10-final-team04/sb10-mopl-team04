package com.team04.mopl.playlist.dto.row;

import java.util.UUID;

public record PlaylistSubscriberCountRow(

	UUID playlistId,
	long subscriberCount
) {
}
