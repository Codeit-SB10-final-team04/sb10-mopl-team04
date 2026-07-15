package com.team04.mopl.content.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.team04.mopl.common.redis.RedisMessagePublisher;
import com.team04.mopl.content.dto.request.ContentChatSendRequest;
import com.team04.mopl.content.dto.response.ContentChatDto;
import com.team04.mopl.content.service.ContentChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ContentChatController {

	private final RedisMessagePublisher redisMessagePublisher;
	private final ContentChatService contentChatService;

	// 채팅 메시지를 Redis Pub/Sub으로 모든 서버에 전파
	@MessageMapping("/contents/{contentId}/chat")
	public void sendMessage(
		@DestinationVariable UUID contentId,
		@Valid @Payload ContentChatSendRequest request,
		Principal principal
	) {
		ContentChatDto chatMessage = contentChatService.createChatMessage(principal, request);
		redisMessagePublisher.publish("/sub/contents/" + contentId + "/chat", chatMessage);
	}
}
