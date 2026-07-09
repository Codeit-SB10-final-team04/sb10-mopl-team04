package com.team04.mopl.directmessage.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.directmessage.dto.request.DirectMessagePageRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.service.DirectMessageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class DirectMessageController implements DirectMessageControllerDocs {

	private final DirectMessageService directMessageService;

	@Override
	@PostMapping("/{conversationId}/direct-messages/{directMessageId}/read")
	public ResponseEntity<Void> createDirectMessageReadStatus(
		@PathVariable UUID conversationId,
		@PathVariable UUID directMessageId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		directMessageService.markAsRead(conversationId, directMessageId, moplUserDetails.getUserId());

		return ResponseEntity.status(HttpStatus.OK).build();
	}

	@Override
	@GetMapping("/{conversationId}/direct-messages")
	public ResponseEntity<CursorResponseDirectMessageDto> findAll(
		@PathVariable UUID conversationId,
		@ModelAttribute DirectMessagePageRequest directMessagePageRequest,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		CursorResponseDirectMessageDto cursorResponseDirectMessageDto = directMessageService.findAll(
			conversationId,
			directMessagePageRequest,
			moplUserDetails.getUserId()
		);

		return ResponseEntity.status(HttpStatus.OK).body(cursorResponseDirectMessageDto);
	}
}
