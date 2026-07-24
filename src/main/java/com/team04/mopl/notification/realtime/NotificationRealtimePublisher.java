package com.team04.mopl.notification.realtime;

import com.team04.mopl.notification.dto.response.NotificationDto;

// 알림 실시간 전송 요청을 추상화한 인터페이스
// 알림 저장 이후 실시간 전송 로직을 Consumer로 분리
// 로컬 SSE 전송이나 Redis Pub/Sub 기반 전송으로 교체 가능
public interface NotificationRealtimePublisher {

	void publish(NotificationDto notificationDto);
}
