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

	// 중복 알림으로 저장에서 제외된 알림 수신자 수 Counter에 사용하는 메트릭 이름
	private static final String NOTIFICATION_DUPLICATE_SKIPPED = "mopl.notification.duplicate.skipped";

	// 알림 저장 실패 횟수 Counter에 사용하는 메트릭 이름
	private static final String NOTIFICATION_STORE_FAILURE = "mopl.notification.store.failure";

	// 알림 한 건의 실시간 Publish 성공/실패 Counter에 사용하는 메트릭 이름
	private static final String NOTIFICATION_REALTIME_PUBLISH = "mopl.notification.realtime.publish";

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
	public void recordCreated(NotificationType type, long notificationCount) {
		if (notificationCount <= 0) {
			return;
		}

		meterRegistry.counter(
			NOTIFICATION_CREATED,
			"type", toTypeTag(type)
		).increment(notificationCount);
	}

	// 중복 알림으로 저장에서 제외된 수신자 수를 알림 타입별로 기록
	public void recordDuplicateSkipped(NotificationType type, long skippedReceiverCount) {
		if (skippedReceiverCount <= 0) {
			return;
		}

		meterRegistry.counter(
			NOTIFICATION_DUPLICATE_SKIPPED,
			"type", toTypeTag(type)
		).increment(skippedReceiverCount);
	}

	// 알림 저장 실패 횟수를 알림 타입별로 기록
	public void recordStoreFailure(NotificationType notificationType) {
		meterRegistry.counter(
			NOTIFICATION_STORE_FAILURE,
			"type", toTypeTag(notificationType)
		).increment();
	}

	// 알림 한 건의 실시간 Publish 성공/실패를 알림 타입별로 기록
	public void recordRealtimePublish(NotificationType type, String result) {
		meterRegistry.counter(
			NOTIFICATION_REALTIME_PUBLISH,
			"type", toTypeTag(type),
			"result", result
		).increment();
	}

	private String toTypeTag(NotificationType type) {
		return type.name().toLowerCase(Locale.ROOT);
	}
}
