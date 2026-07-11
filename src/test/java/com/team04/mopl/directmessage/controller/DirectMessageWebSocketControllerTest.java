package com.team04.mopl.directmessage.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.security.Principal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.service.DirectMessageService;
import com.team04.mopl.user.entity.UserRole;

@ExtendWith(MockitoExtension.class)
class DirectMessageWebSocketControllerTest {

	@Mock
	private DirectMessageService directMessageService;

	@Mock
	private SimpMessagingTemplate simpMessagingTemplate;

	@InjectMocks
	private DirectMessageWebSocketController directMessageWebSocketController;

	@Test
	@DisplayName("성공: 올바른 메시지 전송 요청 시 Service를 호출하고 템플릿을 통해 메시지를 브로드캐스팅한다.")
	void sendDirectMessages_Success() {
		// given
		UUID conversationId = UUID.randomUUID();

		UUID senderId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(senderId, "test@test.com", UserRole.USER);
		Principal principal = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

		DirectMessageSendRequest request = new DirectMessageSendRequest("웹소켓 테스트 메시지");
		DirectMessageDto expectedDto = mock(DirectMessageDto.class);

		given(directMessageService.create(conversationId, request, senderId)).willReturn(expectedDto);

		// when
		directMessageWebSocketController.sendDirectMessages(conversationId, request, principal);

		// then
		verify(directMessageService).create(conversationId, request, senderId);
		verify(simpMessagingTemplate).convertAndSend(
			"/sub/conversations/" + conversationId + "/direct-messages",
			expectedDto
		);
	}

	@Test
	@DisplayName("실패: 인증 정보(Principal)가 유효하지 않으면 AuthException이 발생한다.")
	void sendDirectMessages_Fail_InvalidPrincipal() {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessageSendRequest request = new DirectMessageSendRequest("웹소켓 테스트 메시지");

		// UsernamePasswordAuthenticationToken이 아닌 잘못된 Principal 주입
		Principal invalidPrincipal = mock(Principal.class);

		// when & then
		assertThatThrownBy(
			() -> directMessageWebSocketController.sendDirectMessages(conversationId, request, invalidPrincipal))
			.isInstanceOf(AuthException.class)
			.hasMessageContaining(AuthErrorCode.AUTH_SESSION_INVALID.getMessage());

		verify(directMessageService, never()).create(any(), any(), any());
		verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("성공: MethodArgumentNotValidException 발생 시 커스텀 예외(DM_BLANK)로 변환하여 던진다.")
	void handleValidationException_Success() {
		// given
		MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);

		// when & then
		assertThatThrownBy(() -> directMessageWebSocketController.handleValidationException(exception))
			.isInstanceOf(DirectMessageException.class)
			.hasMessageContaining(DirectMessageErrorCode.DM_BLANK.getMessage());
	}
}