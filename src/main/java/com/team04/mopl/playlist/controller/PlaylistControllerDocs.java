package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스  API")
public interface PlaylistControllerDocs {

	@Operation(summary = "플레이리스트 등록", description = "생성한 플레이리스트는 API 요청자 본인의 플레이리스트로 생성됩니다.")
	ResponseEntity<PlaylistDto> createPlaylist(
		PlaylistCreateRequest request,
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);
}
