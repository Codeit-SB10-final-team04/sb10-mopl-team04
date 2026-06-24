package com.team04.mopl.playlist.dto.row;

import java.util.UUID;

import com.team04.mopl.content.entity.Content;

public record PlaylistContentRow(

	UUID playlistId,
	Content content
) {
}
