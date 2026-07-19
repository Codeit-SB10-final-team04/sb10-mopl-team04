package com.team04.mopl.watching.interceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.event.WatchingSessionEvent;
import com.team04.mopl.watching.service.WatchingSessionService;

class StompWatchingInterceptorTest {

	private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final MessageChannel channel = mock(MessageChannel.class);

	private final StompWatchingInterceptor interceptor = new StompWatchingInterceptor(
		watchingSessionService,
		eventPublisher
	);

	@Test
	@DisplayName("SUBSCRIBE /watch 시 시청 세션 입장 처리")
	void handleSubscribe_joinsWatchingSession() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		WatchingSessionChange change = mock(WatchingSessionChange.class);

		StompHeaderAccessor accessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		accessor.setDestination("/sub/contents/" + contentId + "/watch");
		accessor.setSubscriptionId("sub-0");
		var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.join(eq(contentId), eq(userId), anyString()))
			.thenReturn(Optional.of(change));

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).join(eq(contentId), eq(userId), anyString());
		verify(watchingSessionService).publishJoinAfterSubscriptionReady(
			anyString(), eq("/sub/contents/" + contentId + "/watch"), eq(contentId), eq(change));
	}

	@Test
	@DisplayName("SUBSCRIBE /chat 시 시청 중이 아니면 예외 발생")
	void handleSubscribe_throwsException_whenNotWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		StompHeaderAccessor accessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		accessor.setDestination("/sub/contents/" + contentId + "/chat");
		var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.isWatching(contentId, userId)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	@DisplayName("UNSUBSCRIBE /watch 시 시청 세션 퇴장 처리")
	void handleUnsubscribe_leavesWatchingSession() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String sessionId = "session-1";
		WatchingSessionChange change = mock(WatchingSessionChange.class);

		// 먼저 SUBSCRIBE로 매핑 등록
		StompHeaderAccessor subAccessor = createAccessor(StompCommand.SUBSCRIBE, userId);
		subAccessor.setSessionId(sessionId);
		subAccessor.setDestination("/sub/contents/" + contentId + "/watch");
		subAccessor.setSubscriptionId("sub-0");
		var subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

		when(watchingSessionService.join(eq(contentId), eq(userId), anyString()))
			.thenReturn(Optional.empty());

		interceptor.preSend(subMessage, channel);

		// UNSUBSCRIBE
		StompHeaderAccessor unsubAccessor = createAccessor(StompCommand.UNSUBSCRIBE, userId);
		unsubAccessor.setSessionId(sessionId);
		unsubAccessor.setSubscriptionId("sub-0");
		var unsubMessage = MessageBuilder.createMessage(new byte[0], unsubAccessor.getMessageHeaders());

		when(watchingSessionService.leave(eq(contentId), eq(userId), anyString()))
			.thenReturn(Optional.of(change));

		// when
		interceptor.preSend(unsubMessage, channel);

		// then
		verify(watchingSessionService).leave(eq(contentId), eq(userId), anyString());
	}

	@Test
	@DisplayName("DISCONNECT 시 세션 정리")
	void handleDisconnect_cleansUp() {
		// given
		String sessionId = "session-1";

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		accessor.setSessionId(sessionId);
		accessor.setLeaveMutable(true);
		var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(watchingSessionService.leaveBySessionId(sessionId)).thenReturn(Optional.empty());

		// when
		interceptor.preSend(message, channel);

		// then
		verify(watchingSessionService).leaveBySessionId(sessionId);
	}

	@Test
	@DisplayName("인증 정보 없으면 무시")
	void handleSubscribe_ignoresNonAuthMessages() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/sub/contents/" + UUID.randomUUID() + "/watch");
		accessor.setSessionId("session-1");
		accessor.setLeaveMutable(true);
		var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(MessageDeliveryException.class);

		verifyNoInteractions(watchingSessionService);
	}

	private StompHeaderAccessor createAccessor(StompCommand command, UUID userId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setSessionId("session-" + UUID.randomUUID().toString().substring(0, 8));
		accessor.setLeaveMutable(true);

		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			userId, "test@test.com", com.team04.mopl.user.entity.UserRole.USER
		);
		accessor.setUser(new UsernamePasswordAuthenticationToken(
			userDetails, null, userDetails.getAuthorities()
		));

		return accessor;
	}
}
