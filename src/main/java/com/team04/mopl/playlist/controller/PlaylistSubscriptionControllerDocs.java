package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 API")
public interface PlaylistSubscriptionControllerDocs {

	@Operation(summary = "플레이리스트 구독")
	ResponseEntity<Void> subscribePlaylist(
		UUID playlistId,
		MoplUserDetails moplUserDetails
	);
}
