package com.team04.mopl.playlist.repository;

import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPage;

public interface PlaylistQdslRepository {

	PlaylistCursorPage findPlaylists(PlaylistSearchRequest request);
}
