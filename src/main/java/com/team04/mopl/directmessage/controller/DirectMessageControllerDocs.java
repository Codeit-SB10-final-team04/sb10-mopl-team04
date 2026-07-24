package com.team04.mopl.directmessage.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.directmessage.dto.request.DirectMessagePageRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "다이렉트 메시지", description = "대화방 및 메시지 관리 API")
public interface DirectMessageControllerDocs {

	@Operation(
		summary = "DM 읽음 상태 생성",
		description = "사용자가 DM을 읽은 경우, 읽음 상태를 생성합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> createDirectMessageReadStatus(
		@Parameter(description = "대화 ID") UUID conversationId,
		@Parameter(description = "다이렉트 메시지 ID") UUID directMessageId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "DM 목록 조회",
		description = "특정 대화방에서 발행된 다이렉트 메시지 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<CursorResponseDirectMessageDto> findAll(
		@Parameter(description = "대화 ID") UUID conversationId,
		DirectMessagePageRequest directMessagePageRequest,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);
}
