package com.team04.mopl.content.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.content.dto.request.ContentChatSendRequest;
import com.team04.mopl.content.dto.response.ContentChatDto;
import com.team04.mopl.content.service.ContentChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ContentChatController {

	private final SimpMessagingTemplate messagingTemplate;
	private final ContentChatService contentChatService;

	@MessageMapping("/contents/{contentId}/chat")
	public void sendMessage(
		@DestinationVariable UUID contentId,
		@Payload ContentChatSendRequest request,
		Principal principal
	) {
		ContentChatDto chatMessage = contentChatService.createChatMessage(principal, request);

		messagingTemplate.convertAndSend(
			"/sub/contents/" + contentId + "/chat",
			chatMessage
		);
	}
}
