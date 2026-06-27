package com.team04.mopl.playlist.dto.response;

import java.util.List;

public record PlaylistCursorPage(

	List<PlaylistRow> playlistRows,
	Boolean hasNext,
	Long totalCount
) {
}
