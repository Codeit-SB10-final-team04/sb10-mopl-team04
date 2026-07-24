package com.team04.mopl.watching.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.redis.RedisMessagePublisher;

import lombok.RequiredArgsConstructor;

// 시청 세션 변경 이벤트 → Redis Pub/Sub으로 전파 (순환 의존성 방지를 위해 이벤트 리스너 유지)
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {

	private final RedisMessagePublisher redisMessagePublisher;

	@EventListener
	public void handleWatchingSessionEvent(WatchingSessionEvent event) {
		redisMessagePublisher.publish(
			"/sub/contents/" + event.contentId() + "/watch",
			event.change()
		);
	}
}
