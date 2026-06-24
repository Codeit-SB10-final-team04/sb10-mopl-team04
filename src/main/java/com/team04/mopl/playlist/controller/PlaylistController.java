package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.service.PlaylistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController implements PlaylistControllerDocs {

	private final PlaylistService playlistService;

	@PostMapping
	@Override
	public ResponseEntity<PlaylistDto> createPlaylist(
		@Valid @RequestBody PlaylistCreateRequest request
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		// TODO: Security 구현 완료 후 @AuthenticationPrincipal 사용
		// TODO: Security 구현 완료 후 주석 해제
		// UUID currentUserId = moplUserDetails.getId();
		UUID currentUserId = UUID.fromString("506a74ce-564a-4890-9f12-c2ffd3ef4c4b");
		PlaylistDto playlistDto = playlistService.createPlaylist(request, currentUserId);

		return ResponseEntity.status(HttpStatus.CREATED).body(playlistDto);
	}
}
