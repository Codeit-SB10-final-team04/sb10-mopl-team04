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

// STOMP SUBSCRIBE/SEND/UNSUBSCRIBE/DISCONNECT 프레임을 처리하는 시청 세션 인터셉터
// SUBSCRIBE /watch → 시청 세션 입장 (인메모리 Store + JOIN broadcast)
// SUBSCRIBE /chat → watch 구독 상태 검증 (시청 중이 아니면 거부)
// SEND /pub/.../chat → watch 구독 상태 검증 (구독 없이 발송하는 우회 차단)
// UNSUBSCRIBE /watch → 시청 세션 퇴장 (인메모리 Store + LEAVE broadcast)
// DISCONNECT → 모든 시청 세션 정리 (비정상 종료/탭 닫기 대비)
// broadcast는 순환 의존성 방지를 위해 ApplicationEventPublisher → WatchingSessionEventListener를 통해 처리
@Slf4j
@Component
@RequiredArgsConstructor
public class StompWatchingInterceptor implements ChannelInterceptor {

	// /sub/contents/{contentId}/watch 또는 /sub/contents/{contentId}/chat 패턴
	private static final Pattern SUB_CONTENT_DESTINATION =
		Pattern.compile("^/sub/contents/([^/]+)/(watch|chat)$");

	// /pub/contents/{contentId}/chat 패턴 (채팅 메시지 발송)
	private static final Pattern PUB_CHAT_DESTINATION =
		Pattern.compile("^/pub/contents/([^/]+)/chat$");

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
			case SEND -> handleSend(message, accessor);
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
			// 구독 정보 저장 (UNSUBSCRIBE 시 역추적용) - contentId 검증 통과 후 저장
			if (subscriptionId != null) {
				subscriptionStore.register(sessionId, subscriptionId, destination);
			}

			watchingSessionService.join(contentId, userId, sessionId)
				.ifPresent(change -> eventPublisher.publishEvent(new WatchingSessionEvent(contentId, change)));
		} else if ("chat".equals(type)) {
			// 시청 세션에 참여한 사용자만 채팅 구독 가능
			if (!watchingSessionService.isWatching(contentId, userId)) {
				throw new MessageDeliveryException(message, "콘텐츠 시청 세션에 먼저 참여해야 합니다.");
			}
		}
	}

	// SEND: 채팅 메시지 발송 시 watch 구독 상태 검증 (구독 없이 발송하는 우회 차단)
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

	// UNSUBSCRIBE: watch 구독 해제 시 시청 세션 퇴장
	private void handleUnsubscribe(StompHeaderAccessor accessor) {
		String sessionId = accessor.getSessionId();
		String subscriptionId = accessor.getSubscriptionId();

		if (sessionId == null || subscriptionId == null) {
			return;
		}

		// destination 복원
		subscriptionStore.getDestination(sessionId, subscriptionId)
			.ifPresent(destination -> {
				Matcher matcher = SUB_CONTENT_DESTINATION.matcher(destination);

				if (matcher.matches() && "watch".equals(matcher.group(2))) {
					UUID contentId = UUID.fromString(matcher.group(1));
					UUID userId = getUserIdFromSession(sessionId);

					watchingSessionService.leave(contentId, userId, sessionId)
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
				// 시청 중인 모든 콘텐츠에서 퇴장 처리 (sessionId 전달로 멀티탭 참조 카운팅)
				watchingSessionService.getWatchingContentIds(userId)
					.forEach(contentId ->
						watchingSessionService.leave(contentId, userId, sessionId)
							.ifPresent(change -> eventPublisher.publishEvent(
								new WatchingSessionEvent(contentId, change)))
					);

				subscriptionStore.removeAllBySession(sessionId);
				webSocketSessionStore.remove(sessionId);

				log.info("[WATCHING_SESSION_DISCONNECT] WebSocket 연결 종료 정리 완료: sessionId={}, userId={}",
					sessionId, userId);
			});
	}

	// destination의 contentId 문자열을 UUID로 파싱
	private UUID parseContentId(Message<?> message, String rawContentId) {
		try {
			return UUID.fromString(rawContentId);
		} catch (IllegalArgumentException e) {
			throw new MessageDeliveryException(message, "잘못된 콘텐츠 ID입니다.");
		}
	}

	// accessor의 Principal에서 userId 추출 (타입이 다르면 null 반환)
	private UUID getUserId(StompHeaderAccessor accessor) {
		if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)) {
			return null;
		}

		if (!(auth.getPrincipal() instanceof MoplUserDetails userDetails)) {
			return null;
		}

		return userDetails.getUserId();
	}

	// 세션 스토어에서 userId 조회
	private UUID getUserIdFromSession(String sessionId) {
		return webSocketSessionStore.getUserId(sessionId).orElse(null);
	}
}
