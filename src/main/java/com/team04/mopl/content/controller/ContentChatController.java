package com.team04.mopl.content.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Controller;

import com.team04.mopl.common.redis.RedisMessagePublisher;
import com.team04.mopl.content.dto.request.ContentChatSendRequest;
import com.team04.mopl.content.dto.response.ContentChatDto;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.service.ContentChatService;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ContentChatController {

	private final RedisMessagePublisher redisMessagePublisher;
	private final ContentChatService contentChatService;
	private final MeterRegistry meterRegistry;

	// 채팅 메시지를 Redis Pub/Sub으로 모든 서버에 전파
	@MessageMapping("/contents/{contentId}/chat")
	public void sendMessage(
		@DestinationVariable UUID contentId,
		@Valid @Payload ContentChatSendRequest request,
		Principal principal
	) {
		ContentChatDto chatMessage = contentChatService.createChatMessage(principal, request);

		try {
			redisMessagePublisher.publish("/sub/contents/" + contentId + "/chat", chatMessage);

			// 커스텀 메트릭: 콘텐츠 발행 성공
			meterRegistry.counter(
				"mopl.content.chat.publish",
				"result", "success"
			).increment();
		} catch (Exception e) {
			// 커스텀 메트릭: 콘텐츠 발행 실패
			meterRegistry.counter(
				"mopl.content.chat.publish",
				"result", "failure"
			).increment();

			throw e;
		}
	}

	@MessageExceptionHandler(MethodArgumentNotValidException.class)
	public void handleValidationException(MethodArgumentNotValidException ex) {
		// 커스텀 메트릭: 채팅 전송 거부
		meterRegistry.counter(
			"mopl.content.chat.rejected",
			"reason", "invalid_format"
		).increment();

		throw new ContentException(ContentErrorCode.CONTENT_INVALID_INPUT);
	}
}
