package com.team04.mopl.playlist.dto.response;

import java.util.List;

import com.team04.mopl.playlist.dto.row.PlaylistRow;

public record PlaylistCursorPageDto(

	List<PlaylistRow> playlistRows,
	boolean hasNext,
	long totalCount
) {

	public PlaylistCursorPageDto {
		playlistRows = (playlistRows == null)
			? List.of()
			: List.copyOf(playlistRows);
	}
}
