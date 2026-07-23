package com.team04.mopl.playlist.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 API")
public interface PlaylistContentControllerDocs {

	@Operation(summary = "플레이리스트에 콘텐츠 추가", description = "플레이리스트 소유자만 콘텐츠를 추가할 수 있습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> addContentToPlaylist(
		@Parameter(description = "플레이리스트 ID") UUID playlistId,
		@Parameter(description = "콘텐츠 ID") UUID contentId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(summary = "플레이리스트에서 콘텐츠 삭제", description = "플레이리스트 소유자만 콘텐츠를 삭제할 수 있습니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> deleteContentFromPlaylist(
		@Parameter(description = "플레이리스트 ID") UUID playlistId,
		@Parameter(description = "콘텐츠 ID") UUID contentId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);
}
