package com.team04.mopl.watching.interceptor;

import java.util.UUID;
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
import com.team04.mopl.watching.store.SubscriptionStore;
import com.team04.mopl.watching.store.WebSocketSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// STOMP SUBSCRIBE/UNSUBSCRIBE/DISCONNECT 프레임을 처리하는 시청 세션 인터셉터
// SUBSCRIBE /watch → 시청 세션 입장 (인메모리 Store + JOIN broadcast)
// SUBSCRIBE /chat → watch 구독 상태 검증 (시청 중이 아니면 거부)
// UNSUBSCRIBE /watch → 시청 세션 퇴장 (인메모리 Store + LEAVE broadcast)
// DISCONNECT → 모든 시청 세션 정리 (비정상 종료/탭 닫기 대비)
// broadcast는 순환 의존성 방지를 위해 ApplicationEventPublisher → WatchingSessionEventListener를 통해 처리
@Slf4j
@Component
@RequiredArgsConstructor
public class StompWatchingInterceptor implements ChannelInterceptor {

	// /sub/contents/{contentId}/watch 또는 /sub/contents/{contentId}/chat 패턴
	private static final Pattern CONTENT_DESTINATION =
		Pattern.compile("^/sub/contents/([^/]+)/(watch|chat)$");

	private final WebSocketSessionStore webSocketSessionStore;
	private final SubscriptionStore subscriptionStore;
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
			case UNSUBSCRIBE -> handleUnsubscribe(accessor);
			case DISCONNECT -> handleDisconnect(accessor);
			default -> {
			}
		}

		return message;
	}

	// SUBSCRIBE: watch 구독 시 시청 세션 입장, chat 구독 시 watch 상태 검증
	private void handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		String sessionId = accessor.getSessionId();
		String subscriptionId = accessor.getSubscriptionId();

		if (destination == null || sessionId == null) {
			return;
		}

		// 구독 정보 저장 (UNSUBSCRIBE 시 역추적용)
		if (subscriptionId != null) {
			subscriptionStore.register(sessionId, subscriptionId, destination);
		}

		Matcher matcher = CONTENT_DESTINATION.matcher(destination);
		if (!matcher.matches()) {
			return;
		}

		UUID contentId;
		try {
			contentId = UUID.fromString(matcher.group(1));
		} catch (IllegalArgumentException e) {
			throw new MessageDeliveryException(message, "잘못된 콘텐츠 ID입니다.");
		}

		String type = matcher.group(2);
		UUID userId = getUserId(accessor);

		if (userId == null) {
			return;
		}

		if ("watch".equals(type)) {
			watchingSessionService.join(contentId, userId)
				.ifPresent(change -> eventPublisher.publishEvent(new WatchingSessionEvent(contentId, change)));
		} else if ("chat".equals(type)) {
			if (!watchingSessionService.isWatching(contentId, userId)) {
				throw new MessageDeliveryException(message, "콘텐츠 시청 세션에 먼저 참여해야 합니다.");
			}
		}
	}

	// UNSUBSCRIBE: watch 구독 해제 시 시청 세션 퇴장
	private void handleUnsubscribe(StompHeaderAccessor accessor) {
		String sessionId = accessor.getSessionId();
		String subscriptionId = accessor.getSubscriptionId();

		if (sessionId == null || subscriptionId == null) {
			return;
		}

		subscriptionStore.getDestination(sessionId, subscriptionId)
			.ifPresent(destination -> {
				Matcher matcher = CONTENT_DESTINATION.matcher(destination);

				if (matcher.matches() && "watch".equals(matcher.group(2))) {
					UUID contentId = UUID.fromString(matcher.group(1));
					UUID userId = getUserIdFromSession(sessionId);

					// 시청 세션 퇴장
					watchingSessionService.leave(contentId, userId)
						.ifPresent(change -> eventPublisher.publishEvent(
							new WatchingSessionEvent(contentId, change)));
				}

				subscriptionStore.remove(sessionId, subscriptionId);
			});
	}

	// DISCONNECT: 모든 시청 세션 정리 + 스토어 정리
	private void handleDisconnect(StompHeaderAccessor accessor) {
		String sessionId = accessor.getSessionId();

		if (sessionId == null) {
			return;
		}

		webSocketSessionStore.getUserId(sessionId)
			.ifPresent(userId -> {
				// 시청 중인 모든 콘텐츠에서 퇴장 처리
				watchingSessionService.getWatchingContentIds(userId)
					.forEach(contentId ->
						watchingSessionService.leave(contentId, userId)
							.ifPresent(change -> eventPublisher.publishEvent(
								new WatchingSessionEvent(contentId, change)))
					);

				subscriptionStore.removeAllBySession(sessionId);
				webSocketSessionStore.remove(sessionId);

				log.debug("WebSocket 연결 종료 정리 완료: sessionId={}, userId={}", sessionId, userId);
			});
	}

	// accessor의 Principal에서 userId 추출
	private UUID getUserId(StompHeaderAccessor accessor) {
		if (accessor.getUser() == null) {
			return null;
		}

		UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken)accessor.getUser();
		MoplUserDetails userDetails = (MoplUserDetails)auth.getPrincipal();

		return userDetails.getUserId();
	}

	// 세션 스토어에서 userId 조회
	private UUID getUserIdFromSession(String sessionId) {
		return webSocketSessionStore.getUserId(sessionId).orElse(null);
	}
}
