package com.team04.mopl.conversation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "다이렉트 메시지", description = "대화방 및 메시지 관리 API")
public interface ConversationControllerDocs {

	@Operation(
		summary = "대화 생성",
		description = "특정 사용자와의 대화방을 생성합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<ConversationDto> createConversation(
		ConversationCreateRequest conversationCreateRequest,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "대화 단건 조회",
		description = "대화 ID를 통해 특정 대화방을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "해당 리소스 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<ConversationDto> findConversationById(
		@Parameter(description = "대화 ID") UUID conversationId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "특정 사용자와의 대화 조회",
		description = "대화 상대 ID를 통해 특정 대화방을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "해당 리소스 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<ConversationDto> findConversationByUserId(
		@Parameter(description = "대화 상대 사용자 ID") UUID userId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "대화 목록 조회",
		description = "특정 사용자가 참여하고 있는 대화방 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<CursorResponseConversationDto> findAll(
		ConversationPageRequest conversationPageRequest,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails
	);
}
