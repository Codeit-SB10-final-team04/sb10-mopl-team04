package com.team04.mopl.common.redis;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Redis 채널에서 수신한 메시지를 로컬 WebSocket 클라이언트에게 전달
// 자기가 발행한 메시지는 스킵 (Publisher에서 이미 로컬 전달 완료)
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

	private final SimpMessagingTemplate messagingTemplate;
	private final ObjectMapper objectMapper;

	public void onMessage(String message) {
		try {
			StompBroadcastMessage broadcastMessage = objectMapper.readValue(message, StompBroadcastMessage.class);

			// 자기가 발행한 메시지는 스킵 (Publisher에서 이미 로컬에 전달함)
			if (RedisMessagePublisher.SERVER_ID.equals(broadcastMessage.serverId())) {
				return;
			}

			messagingTemplate.convertAndSend(broadcastMessage.destination(), broadcastMessage.payload());
			log.debug("[REDIS_SUB] 다른 서버 메시지 수신: destination={}", broadcastMessage.destination());
		} catch (Exception e) {
			log.error("[REDIS_SUB] 메시지 처리 실패", e);
		}
	}
}
