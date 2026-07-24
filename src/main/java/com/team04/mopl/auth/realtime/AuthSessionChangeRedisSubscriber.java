package com.team04.mopl.auth.realtime;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionChangeRedisSubscriber implements MessageListener {

	private final ApplicationEventPublisher applicationEventPublisher;

	// Redis 인증 세션 변경 메시지의 로컬 이벤트 변환
	@Override
	public void onMessage(Message message, @Nullable byte[] pattern) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			UUID userId = UUID.fromString(body);

			applicationEventPublisher.publishEvent(new AuthSessionChangedEvent(userId));
		} catch (IllegalArgumentException exception) {
			log.warn("[AUTH_SESSION_CHANGE_MESSAGE_INVALID] 인증 세션 변경 메시지 형식 오류", exception);
		} catch (RuntimeException exception) {
			log.error("[AUTH_SESSION_CHANGE_MESSAGE_HANDLE_FAILED] 인증 세션 변경 메시지 처리 실패", exception);
		}
	}
}
