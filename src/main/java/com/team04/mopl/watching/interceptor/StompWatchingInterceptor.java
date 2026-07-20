package com.team04.mopl.watching.interceptor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.watching.event.WatchingSessionEvent;
import com.team04.mopl.watching.service.WatchingSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompWatchingInterceptor implements ChannelInterceptor {

	private static final Pattern SUB_CONTENT_DESTINATION =
		Pattern.compile("^/sub/contents/([^/]+)/(watch|chat)$");

	private static final Pattern PUB_CHAT_DESTINATION =
		Pattern.compile("^/pub/contents/([^/]+)/chat$");

	// subscriptionId → contentId 매핑 (UNSUBSCRIBE 시 destination 복원용)
	private final ConcurrentHashMap<String, String> watchSubscriptions = new ConcurrentHashMap<>();


	private final WatchingSessionService watchingSessionService;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		switch (accessor.getCommand()) {
			case SUBSCRIBE -> handleSubscribe(message, accessor);
			case SEND -> handleSend(message, accessor);
			case UNSUBSCRIBE -> handleUnsubscribe(accessor);
			case DISCONNECT -> handleDisconnect(accessor);
			default -> {
			}
		}

		return message;
	}

	private void handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		String sessionId = accessor.getSessionId();
		String subscriptionId = accessor.getSubscriptionId();

		if (destination == null || sessionId == null) {
			return;
		}

		Matcher matcher = SUB_CONTENT_DESTINATION.matcher(destination);
		if (!matcher.matches()) {
			return;
		}

		UUID contentId = parseContentId(message, matcher.group(1));
		String type = matcher.group(2);
		UUID userId = getUserId(accessor);

		if (userId == null) {
			throw new MessageDeliveryException(message, "인증 정보가 없습니다.");
		}

		if ("watch".equals(type)) {
			if (subscriptionId != null) {
				watchSubscriptions.put(sessionId + ":" + subscriptionId, contentId.toString());
			}

			watchingSessionService.join(contentId, userId, sessionId)
				.ifPresent(change -> watchingSessionService.publishJoinAfterSubscriptionReady(
					sessionId, destination, contentId, change));
		} else if ("chat".equals(type)) {
			if (!watchingSessionService.isWatching(contentId, userId)) {
				throw new MessageDeliveryException(message, "콘텐츠 시청 세션에 먼저 참여해야 합니다.");
			}
		}
	}

	private void handleSend(Message<?> message, StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();

		if (destination == null) {
			return;
		}

		Matcher matcher = PUB_CHAT_DESTINATION.matcher(destination);
		if (!matcher.matches()) {
			return;
		}

		UUID contentId = parseContentId(message, matcher.group(1));
		UUID userId = getUserId(accessor);

		if (userId == null || !watchingSessionService.isWatching(contentId, userId)) {
			throw new MessageDeliveryException(message, "콘텐츠 시청 세션에 먼저 참여해야 합니다.");
		}
	}

	private void handleUnsubscribe(StompHeaderAccessor accessor) {
		String sessionId = accessor.getSessionId();
		String subscriptionId = accessor.getSubscriptionId();

		if (sessionId == null || subscriptionId == null) {
			return;
		}

		String contentIdStr = watchSubscriptions.remove(sessionId + ":" + subscriptionId);
		if (contentIdStr == null) {
			return;
		}

		UUID contentId = UUID.fromString(contentIdStr);
		UUID userId = getUserId(accessor);

		if (userId != null) {
			watchingSessionService.leave(contentId, userId, sessionId)
				.ifPresent(change -> eventPublisher.publishEvent(
					new WatchingSessionEvent(contentId, change)));
		}
	}

	private void handleDisconnect(StompHeaderAccessor accessor) {
		String sessionId = accessor.getSessionId();

		if (sessionId == null) {
			return;
		}

		watchingSessionService.leaveBySessionId(sessionId)
			.ifPresent(change -> {
				UUID contentId = change.watchingSession().content().id();
				eventPublisher.publishEvent(new WatchingSessionEvent(contentId, change));
			});

		watchSubscriptions.keySet().removeIf(k -> k.startsWith(sessionId + ":"));

		log.info("[WATCHING_SESSION_DISCONNECT] WebSocket 연결 종료 정리 완료: sessionId={}", sessionId);
	}

	private UUID parseContentId(Message<?> message, String rawContentId) {
		try {
			return UUID.fromString(rawContentId);
		} catch (IllegalArgumentException e) {
			throw new MessageDeliveryException(message, "잘못된 콘텐츠 ID입니다.");
		}
	}

	private UUID getUserId(StompHeaderAccessor accessor) {
		if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)) {
			return null;
		}

		if (!(auth.getPrincipal() instanceof MoplUserDetails userDetails)) {
			return null;
		}

		return userDetails.getUserId();
	}

}
