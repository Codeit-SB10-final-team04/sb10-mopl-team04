package com.team04.mopl.conversation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;

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
		UUID currentUserId
		// MoplUserDetails moplUserDetails
	);
}
