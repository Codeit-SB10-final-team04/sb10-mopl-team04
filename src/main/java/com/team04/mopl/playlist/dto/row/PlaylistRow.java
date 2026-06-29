package com.team04.mopl.playlist.dto.row;

import java.util.UUID;

import com.team04.mopl.playlist.entity.Playlist;

public record PlaylistRow(

	Playlist playlist,
	Long subscriberCount,
	UUID ownerId,
	String ownerName,
	String ownerProfileImageUrl
) {
}
