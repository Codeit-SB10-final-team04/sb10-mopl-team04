package com.team04.mopl.watching.interceptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.event.WatchingSessionEvent;
import com.team04.mopl.watching.service.WatchingSessionService;
import com.team04.mopl.watching.store.SubscriptionStore;
import com.team04.mopl.watching.store.WebSocketSessionStore;

class StompWatchingInterceptorTest {

	private final WebSocketSessionStore webSocketSessionStore = mock(WebSocketSessionStore.class);
	private final SubscriptionStore subscriptionStore = mock(SubscriptionStore.class);
	private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final MessageChannel channel = mock(MessageChannel.class);

	private final StompWatchingInterceptor interceptor = new StompWatchingInterceptor(
		webSocketSessionStore,
		subscriptionStore,
		watchingSessionService,
		eventPublisher
	);

	@Test
	@DisplayName("SUBSCRIBE /watch 시 시청 세션 입장 처리 및 이벤트 발행")
	void handleSubscribe_watch_joinsAndPublishesEvent() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		WatchingSessionChange change = mock(WatchingSessionChange.class);

		StompHeaderAccessor accessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		accessor.setDestination("/sub/contents/" + contentId + "/watch");
		accessor.setSubscriptionId("sub-0");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.join(contentId, userId)).thenReturn(Optional.of(change));

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).join(contentId, userId);
		verify(eventPublisher).publishEvent(any(WatchingSessionEvent.class));
		verify(subscriptionStore).register(anyString(), eq("sub-0"), eq("/sub/contents/" + contentId + "/watch"));
	}

	@Test
	@DisplayName("SUBSCRIBE /chat 시 watch 구독 상태를 검증한다")
	void handleSubscribe_chat_verifiesWatchStatus() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		StompHeaderAccessor accessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		accessor.setDestination("/sub/contents/" + contentId + "/chat");
		accessor.setSubscriptionId("sub-1");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.isWatching(contentId, userId)).thenReturn(true);

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).isWatching(contentId, userId);
	}

	@Test
	@DisplayName("SUBSCRIBE /chat 시 watch 구독 안 했으면 예외를 던진다")
	void handleSubscribe_chat_throwsException_whenNotWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		StompHeaderAccessor accessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		accessor.setDestination("/sub/contents/" + contentId + "/chat");
		accessor.setSubscriptionId("sub-1");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.isWatching(contentId, userId)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	@DisplayName("UNSUBSCRIBE /watch 시 시청 세션 퇴장 처리 및 이벤트 발행")
	void handleUnsubscribe_watch_leavesAndPublishesEvent() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String sessionId = "session-1";
		WatchingSessionChange change = mock(WatchingSessionChange.class);

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
		accessor.setSessionId(sessionId);
		accessor.setSubscriptionId("sub-0");
		accessor.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(subscriptionStore.getDestination(sessionId, "sub-0"))
			.thenReturn(Optional.of("/sub/contents/" + contentId + "/watch"));
		when(webSocketSessionStore.getUserId(sessionId)).thenReturn(Optional.of(userId));
		when(watchingSessionService.leave(contentId, userId)).thenReturn(Optional.of(change));

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).leave(contentId, userId);
		verify(eventPublisher).publishEvent(any(WatchingSessionEvent.class));
		verify(subscriptionStore).remove(sessionId, "sub-0");
	}

	@Test
	@DisplayName("UNSUBSCRIBE /chat 시 시청 세션은 유지된다")
	void handleUnsubscribe_chat_doesNotLeave() {
		// given
		UUID contentId = UUID.randomUUID();
		String sessionId = "session-1";

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
		accessor.setSessionId(sessionId);
		accessor.setSubscriptionId("sub-1");
		accessor.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(subscriptionStore.getDestination(sessionId, "sub-1"))
			.thenReturn(Optional.of("/sub/contents/" + contentId + "/chat"));

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService, never()).leave(any(), any());
		verify(subscriptionStore).remove(sessionId, "sub-1");
	}

	@Test
	@DisplayName("DISCONNECT 시 모든 시청 세션 정리 및 스토어 정리")
	void handleDisconnect_cleansUpEverything() {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		String sessionId = "session-1";
		WatchingSessionChange change = mock(WatchingSessionChange.class);

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		accessor.setSessionId(sessionId);
		accessor.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(webSocketSessionStore.getUserId(sessionId)).thenReturn(Optional.of(userId));
		when(watchingSessionService.getWatchingContentIds(userId)).thenReturn(Set.of(contentId));
		when(watchingSessionService.leave(contentId, userId)).thenReturn(Optional.of(change));

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).leave(contentId, userId);
		verify(eventPublisher).publishEvent(any(WatchingSessionEvent.class));
		verify(subscriptionStore).removeAllBySession(sessionId);
		verify(webSocketSessionStore).remove(sessionId);
	}

	@Test
	@DisplayName("SEND /pub/.../chat 시 시청 중이면 통과한다")
	void handleSend_passes_whenWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		StompHeaderAccessor accessor = createAccessor(StompCommand.SEND, userId);
		accessor.setDestination("/pub/contents/" + contentId + "/chat");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.isWatching(contentId, userId)).thenReturn(true);

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).isWatching(contentId, userId);
	}

	@Test
	@DisplayName("SEND /pub/.../chat 시 시청 중이 아니면 예외를 던진다 (구독 없이 발송 차단)")
	void handleSend_throwsException_whenNotWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		StompHeaderAccessor accessor = createAccessor(StompCommand.SEND, userId);
		accessor.setDestination("/pub/contents/" + contentId + "/chat");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.isWatching(contentId, userId)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	@DisplayName("채팅 이외 경로의 SEND 프레임은 처리하지 않는다")
	void preSend_doesNothing_whenSendToOtherDestination() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when
		interceptor.preSend(message, channel);

		// then
		verifyNoInteractions(watchingSessionService, subscriptionStore, webSocketSessionStore);
	}

	// Principal이 바인딩된 STOMP accessor 생성 헬퍼
	private StompHeaderAccessor createAccessor(StompCommand command, UUID userId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setSessionId("session-1");
		accessor.setLeaveMutable(true);

		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
			userDetails, null, userDetails.getAuthorities()
		);
		accessor.setUser(auth);

		return accessor;
	}
}
