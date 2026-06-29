package com.team04.mopl.playlist.dto.response;

import java.util.List;

import com.team04.mopl.playlist.dto.row.PlaylistRow;

public record PlaylistCursorPage(

	List<PlaylistRow> playlistRows,
	Boolean hasNext,
	Long totalCount
) {
}
