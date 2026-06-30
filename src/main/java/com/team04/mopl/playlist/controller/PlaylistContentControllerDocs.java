package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 API")
public interface PlaylistContentControllerDocs {

	@Operation(summary = "플레이리스트에 콘텐츠 추가", description = "플레이리스트 소유자만 콘텐츠를 추가할 수 있습니다.")
	ResponseEntity<Void> addContentToPlaylist(
		UUID playlistId,
		UUID contentId,
		MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트에서 콘텐츠 삭제", description = "플레이리스트 소유자만 콘텐츠를 삭제할 수 있습니다.")
	ResponseEntity<Void> deleteContentFromPlaylist(
		UUID playlistId,
		UUID contentId,
		MoplUserDetails moplUserDetails
	);
}
