package com.team04.mopl.common.redis;

// Redis Pub/Sub을 통해 서버 간 전파되는 STOMP 메시지 래퍼
public record StompBroadcastMessage(
	String serverId,
	String destination,
	Object payload
) {
}
