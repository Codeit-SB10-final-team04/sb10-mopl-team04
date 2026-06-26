package com.team04.mopl.conversation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.service.ConversationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController implements ConversationControllerDocs {

	private final ConversationService conversationService;

	@Override
	@PostMapping
	public ResponseEntity<ConversationDto> createConversation(
		@Valid @RequestBody ConversationCreateRequest conversationCreateRequest,
		@RequestHeader("X-MOPL-USER-ID") UUID currentUserId
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		ConversationDto conversationDto = conversationService.createConversation(conversationCreateRequest,
			currentUserId);

		return ResponseEntity.status(HttpStatus.CREATED).body(conversationDto);
	}
}
