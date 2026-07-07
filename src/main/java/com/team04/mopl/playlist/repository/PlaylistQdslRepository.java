package com.team04.mopl.playlist.repository;

import com.team04.mopl.playlist.dto.request.PlaylistPageRequest;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPage;

public interface PlaylistQdslRepository {

	PlaylistCursorPage findAllPlaylists(PlaylistPageRequest request);
}
