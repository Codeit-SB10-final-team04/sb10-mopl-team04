package com.team04.mopl.watching.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "시청 세션 관리", description = "시청 세션 API")
public interface WatchingSessionControllerDocs {

	@Operation(summary = "특정 콘텐츠의 시청 세션 목록 조회 (커서 페이지네이션)")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<CursorResponse<WatchingSessionDto>> findByContent(
		@Parameter(description = "콘텐츠 ID") UUID contentId,
		WatchingSessionPageRequest request
	);

	@Operation(summary = "특정 사용자의 시청 세션 조회 (nullable)")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<WatchingSessionDto> findByWatcher(@Parameter(description = "시청자 ID") UUID watcherId);
}
