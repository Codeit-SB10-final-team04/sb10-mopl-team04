package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
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
		@Valid @RequestBody PlaylistCreateRequest request,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		PlaylistDto playlistDto = playlistService.createPlaylist(request, currentUserId);

		return ResponseEntity.status(HttpStatus.CREATED).body(playlistDto);
	}

	@GetMapping(value = "/{playlistId}")
	@Override
	public ResponseEntity<PlaylistDto> findPlaylist(
		@PathVariable UUID playlistId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		PlaylistDto playlistDto = playlistService.findPlaylist(playlistId, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(playlistDto);
	}

	@GetMapping
	@Override
	public ResponseEntity<CursorResponsePlaylistDto> findAllPlaylists(
		@Valid @ModelAttribute PlaylistSearchRequest request,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		CursorResponsePlaylistDto cursorResponsePlaylistDto =
			playlistService.findAllPlaylists(request, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(cursorResponsePlaylistDto);
	}

	@PatchMapping(value = "/{playlistId}")
	@Override
	public ResponseEntity<PlaylistDto> updatePlaylist(
		@PathVariable UUID playlistId,
		@Valid @RequestBody PlaylistUpdateRequest request,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();

		PlaylistDto playlistDto = playlistService.updatePlaylist(playlistId, request, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(playlistDto);
	}

	@DeleteMapping(value = "/{playlistId}")
	@Override
	public ResponseEntity<Void> softDeletePlaylist(
		@PathVariable UUID playlistId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();

		playlistService.softDeletePlaylist(playlistId, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(null);
	}
}
