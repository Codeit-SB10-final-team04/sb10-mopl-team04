package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.playlist.service.PlaylistContentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistContentController implements PlaylistContentControllerDocs {

	private final PlaylistContentService playlistContentService;

	@PostMapping(value = "/{playlistId}/contents/{contentId}")
	@Override
	public ResponseEntity<Void> addContentToPlaylist(
		@PathVariable UUID playlistId,
		@PathVariable UUID contentId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@DeleteMapping(value = "/{playlistId}/contents/{contentId}")
	@Override
	public ResponseEntity<Void> deleteContentFromPlaylist(
		@PathVariable UUID playlistId,
		@PathVariable UUID contentId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		playlistContentService.deleteContentFromPlaylist(playlistId, contentId, currentUserId);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
