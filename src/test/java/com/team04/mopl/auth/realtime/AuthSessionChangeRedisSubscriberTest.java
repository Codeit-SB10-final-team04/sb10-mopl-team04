package com.team04.mopl.auth.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;

class AuthSessionChangeRedisSubscriberTest {

	@Test
	@DisplayName("다른 인스턴스의 인증 세션 변경 메시지를 로컬 종료 이벤트로 전달한다")
	void onMessage_publishesLocalEvent_whenRedisMessageIsValid() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		AuthSessionChangeRedisSubscriber subscriber =
			new AuthSessionChangeRedisSubscriber(applicationEventPublisher);
		Message message = mock(Message.class);
		UUID userId = UUID.randomUUID();
		when(message.getBody()).thenReturn(userId.toString().getBytes(StandardCharsets.UTF_8));

		// when
		subscriber.onMessage(message, null);

		// then
		ArgumentCaptor<AuthSessionChangedEvent> eventCaptor =
			ArgumentCaptor.forClass(AuthSessionChangedEvent.class);
		verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("잘못된 Redis 메시지는 로컬 이벤트로 전달하지 않는다")
	void onMessage_ignoresMessage_whenUserIdIsInvalid() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		AuthSessionChangeRedisSubscriber subscriber =
			new AuthSessionChangeRedisSubscriber(applicationEventPublisher);
		Message message = mock(Message.class);
		when(message.getBody()).thenReturn("invalid-user-id".getBytes(StandardCharsets.UTF_8));

		// when
		subscriber.onMessage(message, null);

		// then
		verifyNoInteractions(applicationEventPublisher);
	}

	@Test
	@DisplayName("로컬 이벤트 리스너가 실패해도 Redis 구독 처리를 중단하지 않는다")
	void onMessage_doesNotThrow_whenLocalEventListenerFails() {
		// given
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		AuthSessionChangeRedisSubscriber subscriber =
			new AuthSessionChangeRedisSubscriber(applicationEventPublisher);
		Message message = mock(Message.class);
		UUID userId = UUID.randomUUID();
		AuthSessionChangedEvent event = new AuthSessionChangedEvent(userId);
		when(message.getBody()).thenReturn(userId.toString().getBytes(StandardCharsets.UTF_8));
		doThrow(new IllegalStateException("listener failed"))
			.when(applicationEventPublisher)
			.publishEvent(event);

		// when, then
		assertThatCode(() -> subscriber.onMessage(message, null)).doesNotThrowAnyException();
		verify(applicationEventPublisher).publishEvent(event);
	}
}
