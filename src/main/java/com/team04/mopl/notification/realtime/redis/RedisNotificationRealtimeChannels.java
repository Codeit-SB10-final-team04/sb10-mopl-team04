package com.team04.mopl.notification.realtime.redis;

// 알림 실시간 전송에 사용하는 Redis Pub/Sub 채널 이름을 관리하는 상수 클래스
public final class RedisNotificationRealtimeChannels {

	public static final String NOTIFICATION_REALTIME = "mopl:notification:realtime";

	private RedisNotificationRealtimeChannels() {
	}
}
