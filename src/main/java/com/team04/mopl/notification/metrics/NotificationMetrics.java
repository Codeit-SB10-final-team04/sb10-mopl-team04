package com.team04.mopl.notification.metrics;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.team04.mopl.notification.enums.NotificationType;

import io.micrometer.core.instrument.MeterRegistry;

// 알림 관련 실행 결과, 처리 건수 등을 Micrometer로 기록하는 컴포넌트
@Component
public class NotificationMetrics {

	// 알림 Kafka 이벤트 역직렬화 실패 횟수 Counter에 사용하는 메트릭 이름
	private static final String NOTIFICATION_KAFKA_DESERIALIZATION_FAILURE = "mopl.notification.kafka.deserialization.failure";

	// 저장된 알림 건수 Counter에 사용하는 메트릭 이름
	private static final String NOTIFICATION_CREATED = "mopl.notification.created";

	private final MeterRegistry meterRegistry;

	public NotificationMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	// 알림 Kafka 이벤트 역직렬화 실패 횟수를 이벤트 클래스별로 기록
	public void recordDeserializationFailure(String event) {
		meterRegistry.counter(
			NOTIFICATION_KAFKA_DESERIALIZATION_FAILURE,
			"event", event
		).increment();
	}

	// 저장된 알림 건수를 알림 타입별로 기록
	public void recordCreated(NotificationType type, long count) {
		if (count <= 0) {
			return;
		}

		meterRegistry.counter(
			NOTIFICATION_CREATED,
			"type", type.toString().toLowerCase(Locale.ROOT)
		).increment(count);
	}

	// 중복 제외된 수신자 건수
	public void recordDuplicateSkipped(NotificationType notificationType, long count) {

	}

	// 알림 저장 실패 건수
	public void recordStoreFailure(NotificationType notificationType) {

	}

	// 알림 한 건의 Publisher 호출
	public void recordRealtimePublish(NotificationType notificationType, String result) {

	}
}
