package com.team04.mopl.directmessage.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.service.DirectMessageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

	private final DirectMessageService directMessageService;

	private final SimpMessagingTemplate simpMessagingTemplate;

	@MessageMapping("/conversations/{conversationId}/direct-messages")
	public void sendDirectMessages(
		@DestinationVariable UUID conversationId,
		@Valid @Payload DirectMessageSendRequest directMessageSendRequest,
		Principal principal
	) {
		// 1. 인증 정보로부터 전송자 ID 추출
		UUID senderId = extractUserId(principal);

		// 2. 메시지 생성 및 저장
		DirectMessageDto directMessageDto = directMessageService.create(
			conversationId,
			directMessageSendRequest,
			senderId
		);

		// 3. 메시지 전송
		simpMessagingTemplate.convertAndSend(
			"/sub/conversations/" + conversationId + "/direct-messages",
			directMessageDto
		);
	}

	// 사용자 인증 토큰으로부터 요청자 ID 추출
	private UUID extractUserId(Principal principal) {
		if (principal instanceof UsernamePasswordAuthenticationToken auth) {
			if (auth.getPrincipal() instanceof MoplUserDetails userDetails) {
				return userDetails.getUserId();
			}
		}
		throw new AuthException(AuthErrorCode.AUTH_SESSION_INVALID);
	}
}
