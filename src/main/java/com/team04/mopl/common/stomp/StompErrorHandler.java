package com.team04.mopl.common.stomp;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.common.exception.MoplException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 메시지 처리 중 발생한 예외를 클라이언트에게 안전한 ERROR 프레임으로 변환하는 핸들러
 *
 * <p>이 핸들러가 없으면 인터셉터/핸들러에서 발생한 예외(예: AuthException)가
 * Spring에 의해 MessageDeliveryException으로 감싸지면서 스택 트레이스가 그대로
 * 클라이언트의 STOMP ERROR 프레임에 노출됩니다.</p>
 *
 * <h3>예외 처리 흐름</h3>
 * <pre>
 *   인터셉터에서 AuthException 발생
 *       ↓
 *   Spring이 MessageDeliveryException으로 여러 겹 감쌈
 *       ↓
 *   이 핸들러가 가로채서 중첩된 예외를 끝까지 언래핑
 *       ↓
 *   에러 코드 + 메시지를 JSON body에 담은 STOMP ERROR 프레임으로 변환
 * </pre>
 *
 * <p>REST API의 {@code @ControllerAdvice}와 동일한 역할이지만,
 * WebSocket/STOMP는 서블릿 밖에서 동작하므로 별도 핸들러가 필요합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {

	private final ObjectMapper objectMapper;

	// Spring이 감싼 중첩 예외에서 실제 원인 예외를 꺼내 처리
	@Override
	public Message<byte[]> handleClientMessageProcessingError(
		Message<byte[]> clientMessage,
		Throwable ex
	) {
		Throwable cause = unwrap(ex);

		// 커스텀 예외(AuthException 등)인 경우 → ErrorResponse 공통 포맷으로 전달
		if (cause instanceof MoplException moplException) {
			log.warn("STOMP 처리 오류: code={}, message={}",
				moplException.getErrorCode().getCode(),
				moplException.getMessage());

			return createErrorMessage(
				moplException.getMessage(),
				ErrorResponse.from(moplException)
			);
		}

		// 예상하지 못한 예외 → 내부 정보 노출 없이 일반 메시지만 전달
		log.error("STOMP 예상치 못한 오류", ex);

		return createErrorMessage(
			"서버 내부 오류가 발생했습니다.",
			ErrorResponse.internalServerError()
		);
	}

	/**
	 * Spring이 예외를 여러 겹으로 감쌀 수 있으므로 실제 원인 예외까지 언래핑
	 *
	 * 예: MessageDeliveryException → MessageHandlingException → AuthException(실제 원인)
	 */
	private Throwable unwrap(Throwable ex) {
		Throwable result = ex;

		while (result.getCause() != null
			&& (result instanceof MessageDeliveryException
			|| result instanceof MessageHandlingException)) {
			result = result.getCause();
		}

		return result;
	}

	// STOMP ERROR 프레임 생성 (헤더에 메시지, body에 ErrorResponse JSON)
	private Message<byte[]> createErrorMessage(String headerMessage, ErrorResponse errorResponse) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
		accessor.setMessage(headerMessage);
		accessor.setLeaveMutable(true);

		byte[] payload = toPayload(errorResponse);

		return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
	}

	// ErrorResponse를 JSON 바이트 배열로 직렬화
	private byte[] toPayload(ErrorResponse errorResponse) {
		try {
			return objectMapper.writeValueAsBytes(errorResponse);
		} catch (JsonProcessingException e) {
			return new byte[0];
		}
	}
}
