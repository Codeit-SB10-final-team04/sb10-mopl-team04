package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 API")
public interface PlaylistControllerDocs {

	@Operation(summary = "플레이리스트 등록", description = "생성한 플레이리스트는 API 요청자 본인의 플레이리스트로 생성됩니다.")
	ResponseEntity<PlaylistDto> createPlaylist(
		PlaylistCreateRequest request,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 단건 조회")
	ResponseEntity<PlaylistDto> findPlaylist(
		UUID playlistId,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 목록 조회 (커서 페이지네이션)")
	ResponseEntity<CursorResponsePlaylistDto> findAllPlaylists(
		PlaylistSearchRequest request,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 수정", description = "플레이리스트 소유자만 수정할 수 있습니다.")
	ResponseEntity<PlaylistDto> updatePlaylist(
		UUID playlistId,
		PlaylistUpdateRequest request,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 삭제", description = "플레이리스트 소유자만 삭제할 수 있습니다.")
	ResponseEntity<Void> softDeletePlaylist(
		UUID playlistId,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);
}
