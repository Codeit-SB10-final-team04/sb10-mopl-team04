package com.team04.mopl.directmessage.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.service.DirectMessageService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

	private final DirectMessageService directMessageService;

	private final SimpMessagingTemplate simpMessagingTemplate;

	private final MeterRegistry meterRegistry;

	@MessageMapping("/conversations/{conversationId}/direct-messages")
	public void sendDirectMessages(
		@DestinationVariable UUID conversationId,
		@Valid @Payload DirectMessageSendRequest directMessageSendRequest,
		Principal principal
	) {
		// 커스텀 메트릭: 처리 시간 측정 시작
		Timer.Sample sample = Timer.start(meterRegistry);

		try {
			// 1. 인증 정보로부터 전송자 ID 추출
			UUID senderId = extractUserId(principal);

			// 2. 메시지 생성 및 저장 (해당 메서드 안에서도 별도로 create 메트릭이 수집됨)
			DirectMessageDto directMessageDto = directMessageService.create(
				conversationId,
				directMessageSendRequest,
				senderId
			);

			// 3. 메시지 브로드캐스트 전송
			simpMessagingTemplate.convertAndSend(
				"/sub/conversations/" + conversationId + "/direct-messages",
				directMessageDto
			);

			// 커스텀 메트릭 추가: 브로드캐스트 성공 시간 측정
			sample.stop(meterRegistry.timer(
				"mopl.dm.broadcast.duration",
				"result", "success")
			);
			// 커스텀 메트릭 추가: 브로드캐스트 성공
			meterRegistry.counter(
				"mopl.dm.broadcast",
				"result", "success"
			).increment();

		} catch (Exception e) {
			// 커스텀 메트릭 추가: 브로드캐스트 실패 시간 측정
			sample.stop(meterRegistry.timer(
				"mopl.dm.broadcast.duration",
				"result", "failure")
			);
			// 커스텀 메트릭 추가: 브로드캐스트 실패
			meterRegistry.counter(
				"mopl.dm.broadcast",
				"result", "failure"
			).increment();

			throw e;
		}
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

	// @Valid 검증 실패 시 발생하는 예외를 잡아서 MoplException으로 변환
	@MessageExceptionHandler(MethodArgumentNotValidException.class)
	public void handleValidationException(MethodArgumentNotValidException ex) {
		// 커스텀 메트릭 추가: DM 전송 거부
		meterRegistry.counter(
			"mopl.dm.rejected",
			"reason", "invalid_format"
		).increment();

		// MoplException으로 래핑하여 StompErrorHandler가 ErrorResponse로 변환하도록 위임
		throw new DirectMessageException(DirectMessageErrorCode.DM_BLANK);
	}
}
