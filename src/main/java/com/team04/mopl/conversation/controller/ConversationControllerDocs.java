package com.team04.mopl.conversation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Conversation API", description = "대화 API")
public interface ConversationControllerDocs {

	@Operation(
		summary = "대화 생성",
		description = "특정 사용자와의 대화방을 생성합니다."
	)
	ResponseEntity<ConversationDto> createConversation(
		ConversationCreateRequest conversationCreateRequest,
		MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "대화 단건 조회",
		description = "대화 ID를 통해 특정 대화방을 조회합니다."
	)
	ResponseEntity<ConversationDto> findConversationById(
		UUID conversationId,
		MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "특정 사용자와의 대화 조회",
		description = "대화 상대 ID를 통해 특정 대화방을 조회합니다."
	)
	ResponseEntity<ConversationDto> findConversationByUserId(
		UUID userId,
		MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "대화 목록 조회",
		description = "특정 사용자가 참여하고 있는 대화방 목록을 조회합니다."
	)
	ResponseEntity<CursorResponseConversationDto> findAll(
		ConversationPageRequest conversationPageRequest,
		MoplUserDetails moplUserDetails
	);
}
