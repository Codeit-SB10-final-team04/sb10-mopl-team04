package com.team04.mopl.auth.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.session.AuthSessionStore;

class SseAuthSessionTrackingTest {

	@Test
	@DisplayName("SSE 연결에 인증 세션 ID를 연결하고 종료 시 함께 정리한다")
	void registry_tracksAuthSessionAndRemovesClosedEmitter() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		SseEmitter emitter = mock(SseEmitter.class);
		when(authSessionStore.isActive(receiverId, authSessionId)).thenReturn(true);

		// when
		registry.bind(receiverId, authSessionId, emitter);

		// then
		RealtimeSseSessionRegistry.AuthenticatedSession session =
			new RealtimeSseSessionRegistry.AuthenticatedSession(receiverId, authSessionId, emitter);
		assertThat(registry.findByUserId(receiverId)).containsExactly(session);

		// when
		registry.close(session);

		// then
		verify(emitter).complete();
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}

	@Test
	@DisplayName("바인딩 중 인증 세션이 교체되면 생성된 SSE 연결을 완료한다")
	void bind_completesEmitter_whenAuthSessionWasReplaced() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		SseEmitter emitter = mock(SseEmitter.class);
		when(authSessionStore.isActive(receiverId, authSessionId)).thenReturn(false);

		// when
		registry.bind(receiverId, authSessionId, emitter);

		// then
		verify(emitter).complete();
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}

	@Test
	@DisplayName("인증 세션 ID가 없으면 SSE 연결을 추적하지 않고 완료한다")
	void bind_completesEmitter_whenAuthSessionIdIsMissing() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		SseEmitter emitter = mock(SseEmitter.class);

		// when
		registry.bind(receiverId, null, emitter);

		// then
		verify(emitter).complete();
		verifyNoInteractions(authSessionStore);
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}

	@Test
	@DisplayName("인증 세션 검증 중 오류가 발생하면 SSE 연결을 완료한다")
	void bind_completesEmitter_whenAuthSessionValidationFails() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		SseEmitter emitter = mock(SseEmitter.class);
		when(authSessionStore.isActive(receiverId, authSessionId))
			.thenThrow(new AuthException(AuthErrorCode.AUTH_SERVICE_ERROR));

		// when
		registry.bind(receiverId, authSessionId, emitter);

		// then
		verify(emitter).complete();
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}

	@Test
	@DisplayName("SSE 종료가 실패해도 레지스트리에서는 연결을 제거한다")
	void close_removesSession_whenEmitterCompletionFails() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		SseEmitter emitter = mock(SseEmitter.class);
		when(authSessionStore.isActive(receiverId, authSessionId)).thenReturn(true);
		registry.bind(receiverId, authSessionId, emitter);
		RealtimeSseSessionRegistry.AuthenticatedSession session =
			registry.findByUserId(receiverId).get(0);
		doThrow(new IllegalStateException("complete failed")).when(emitter).complete();

		// when
		boolean result = registry.close(session);

		// then
		assertThat(result).isFalse();
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}

	@Test
	@DisplayName("SSE 완료·시간초과·오류 콜백이 각각 인증 세션 추적을 정리한다")
	@SuppressWarnings("unchecked")
	void lifecycleCallbacks_removeTrackedSessions() {
		// given
		AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
		RealtimeSseSessionRegistry registry = new RealtimeSseSessionRegistry(authSessionStore);
		UUID receiverId = UUID.randomUUID();
		UUID authSessionId = UUID.randomUUID();
		SseEmitter completionEmitter = mock(SseEmitter.class);
		SseEmitter timeoutEmitter = mock(SseEmitter.class);
		SseEmitter errorEmitter = mock(SseEmitter.class);
		when(authSessionStore.isActive(receiverId, authSessionId)).thenReturn(true);

		registry.bind(receiverId, authSessionId, completionEmitter);
		registry.bind(receiverId, authSessionId, timeoutEmitter);
		registry.bind(receiverId, authSessionId, errorEmitter);

		ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
		ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
		ArgumentCaptor<Consumer<Throwable>> errorCaptor =
			ArgumentCaptor.forClass(Consumer.class);
		verify(completionEmitter).onCompletion(completionCaptor.capture());
		verify(timeoutEmitter).onTimeout(timeoutCaptor.capture());
		verify(errorEmitter).onError(errorCaptor.capture());

		// when
		completionCaptor.getValue().run();
		timeoutCaptor.getValue().run();
		errorCaptor.getValue().accept(new IllegalStateException("stream failed"));

		// then
		assertThat(registry.findByUserId(receiverId)).isEmpty();
	}
}
