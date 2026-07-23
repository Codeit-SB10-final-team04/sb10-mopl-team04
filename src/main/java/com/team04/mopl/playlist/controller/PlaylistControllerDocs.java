package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistPageRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 API")
public interface PlaylistControllerDocs {

	@Operation(summary = "플레이리스트 등록", description = "생성한 플레이리스트는 API 요청자 본인의 플레이리스트로 생성됩니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<PlaylistDto> createPlaylist(
		PlaylistCreateRequest request,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 단건 조회")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<PlaylistDto> findPlaylist(
		@Parameter(description = "플레이리스트 ID") UUID playlistId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 목록 조회 (커서 페이지네이션)")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<CursorResponsePlaylistDto> findAllPlaylists(
		PlaylistPageRequest request,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 수정", description = "플레이리스트 소유자만 수정할 수 있습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<PlaylistDto> updatePlaylist(
		@Parameter(description = "플레이리스트 ID") UUID playlistId,
		PlaylistUpdateRequest request,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트 삭제", description = "플레이리스트 소유자만 삭제할 수 있습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> softDeletePlaylist(
		@Parameter(description = "플레이리스트 ID") UUID playlistId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);
}
