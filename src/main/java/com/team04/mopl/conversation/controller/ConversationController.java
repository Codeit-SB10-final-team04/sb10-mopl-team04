package com.team04.mopl.conversation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
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
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		ConversationDto conversationDto = conversationService.createConversation(conversationCreateRequest,
			moplUserDetails);

		return ResponseEntity.status(HttpStatus.CREATED).body(conversationDto);
	}

	@Override
	@GetMapping("/{conversationId}")
	public ResponseEntity<ConversationDto> findConversationById(
		@PathVariable UUID conversationId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		ConversationDto conversationDto = conversationService.findConversationById(conversationId, moplUserDetails);

		return ResponseEntity.status(HttpStatus.OK).body(conversationDto);
	}
}
