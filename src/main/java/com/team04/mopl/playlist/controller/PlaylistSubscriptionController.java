package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.playlist.service.PlaylistSubscriptionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistSubscriptionController implements PlaylistSubscriptionControllerDocs {

	private final PlaylistSubscriptionService playlistSubscriptionService;

	@PostMapping(value = "/{playlistId}/subscription")
	@Override
	public ResponseEntity<Void> subscribePlaylist(
		@PathVariable UUID playlistId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
