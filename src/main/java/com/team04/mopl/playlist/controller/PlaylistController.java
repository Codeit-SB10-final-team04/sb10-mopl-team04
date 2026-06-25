package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
		@Valid @RequestBody PlaylistCreateRequest request,
		@RequestHeader("X-MOPL-USER-ID") UUID currentUserId
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		// TODO: Security 구현 완료 후 @AuthenticationPrincipal 사용
		// Security 구현 완료 전까지 임시 헤더로 받기
		// TODO: Security 구현 완료 후 주석 해제
		// UUID currentUserId = moplUserDetails.getId();
		PlaylistDto playlistDto = playlistService.createPlaylist(request, currentUserId);

		return ResponseEntity.status(HttpStatus.CREATED).body(playlistDto);
	}

	@GetMapping(value = "/{playlistId}")
	@Override
	public ResponseEntity<PlaylistDto> findPlaylist(
		@PathVariable UUID playlistId,
		@RequestHeader("X-MOPL-USER-ID") UUID currentUserId
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		// TODO: Security 구현 완료 후 @AuthenticationPrincipal 사용
		// Security 구현 완료 전까지 임시 헤더로 받기
		// TODO: Security 구현 완료 후 주석 해제
		// UUID currentUserId = moplUserDetails.getId();
		PlaylistDto playlistDto = playlistService.findPlaylist(playlistId, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(playlistDto);
	}
}
