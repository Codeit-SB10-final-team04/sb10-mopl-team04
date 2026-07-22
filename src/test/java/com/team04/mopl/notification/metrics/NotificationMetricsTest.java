package com.team04.mopl.notification.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.notification.enums.NotificationType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class NotificationMetricsTest {

	private MeterRegistry meterRegistry;
	private NotificationMetrics notificationMetrics;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		notificationMetrics = new NotificationMetrics(meterRegistry);
	}

	@Test
	@DisplayName("생성 시 고정된 알림 메트릭 태그 조합을 초기값 0으로 등록한다.")
	void constructor_registerFixedCounterTagsWithZeroCount() {
		List.of("role_change", "subscribe", "content_add", "follow", "following_activity", "dm")
			.forEach(type -> assertAll(
				() -> assertEquals(0.0, getCounter("mopl.notification.saved", "type", type)),
				() -> assertEquals(0.0,
					getCounter("mopl.notification.duplicate.skipped", "type", type)),
				() -> assertEquals(0.0,
					getCounter("mopl.notification.store.failure", "type", type)),
				() -> assertEquals(0.0, getRealtimePublishCount(type, "success")),
				() -> assertEquals(0.0, getRealtimePublishCount(type, "failure"))
			));

		List.of(
			"PlaylistSubscribedEvent",
			"PlaylistContentAddedEvent",
			"FollowCreatedEvent",
			"PlaylistCreatedEvent",
			"UserRoleChangedEvent",
			"DirectMessageCreatedEvent"
		).forEach(event -> assertEquals(0.0, getCounter(
			"mopl.notification.kafka.deserialization.failure",
			"event",
			event
		)));
	}

	@Test
	@DisplayName("Kafka 이벤트 역직렬화 실패를 기록하면 이벤트 클래스별로 Counter를 1 증가시킨다.")
	void recordDeserializationFailure_incrementCounter_whenDeserializationFails() {
		// given
		String event = "PlaylistSubscribedEvent";

		// when
		notificationMetrics.recordDeserializationFailure(event);

		// then
		double count = meterRegistry
			.get("mopl.notification.kafka.deserialization.failure")
			.tag("event", event)
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("저장된 알림 건수가 양수면 알림 저장 Counter를 해당 건수만큼 증가시킨다.")
	void recordSaved_incrementCounterByNotificationCount_whenCountIsPositive() {
		// given
		NotificationType type = NotificationType.SUBSCRIBE;
		long notificationCount = 2L;

		// when
		notificationMetrics.recordSaved(type, notificationCount);

		// then
		double count = meterRegistry
			.get("mopl.notification.saved")
			.tag("type", "subscribe")
			.counter()
			.count();

		assertEquals(2.0, count);
	}

	@Test
	@DisplayName("저장된 알림 건수가 0 이하면 알림 저장 Counter를 증가시키지 않는다.")
	void recordSaved_doesNotIncrementCounter_whenCountIsNotPositive() {
		// given
		NotificationType type = NotificationType.SUBSCRIBE;

		// when
		notificationMetrics.recordSaved(type, 0);

		// then
		assertEquals(0.0, getCounter("mopl.notification.saved", "type", "subscribe"));
	}

	@Test
	@DisplayName("중복으로 제외된 수신자 수가 양수면 중복 제외 Counter를 해당 건수만큼 증가시킨다.")
	void recordDuplicateSkipped_incrementCounterBySkippedCount_whenCountIsPositive() {
		// given
		NotificationType type = NotificationType.CONTENT_ADD;
		long skippedReceiverCount = 1L;

		// when
		notificationMetrics.recordDuplicateSkipped(type, skippedReceiverCount);

		// then
		double count = meterRegistry
			.get("mopl.notification.duplicate.skipped")
			.tag("type", "content_add")
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("중복으로 제외된 수신자 수가 0 이하면 중복 제외 Counter를 증가시키지 않는다.")
	void recordDuplicateSkipped_doesNotIncrementCounter_whenCountIsNotPositive() {
		// given
		NotificationType type = NotificationType.CONTENT_ADD;

		// when
		notificationMetrics.recordDuplicateSkipped(type, 0);

		// then
		assertEquals(0.0,
			getCounter("mopl.notification.duplicate.skipped", "type", "content_add"));
	}

	@Test
	@DisplayName("알림 저장 실패를 기록하면 알림 타입별 저장 실패 Counter를 1 증가시킨다.")
	void recordStoreFailure_incrementCounter() {
		// given
		NotificationType type = NotificationType.FOLLOW;

		// when
		notificationMetrics.recordStoreFailure(type);

		// then
		double count = meterRegistry
			.get("mopl.notification.store.failure")
			.tag("type", "follow")
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("알림 한 건 실시간 Publish 성공과 실패를 결과별 Counter로 분리해 기록한다.")
	void recordRealtimePublish_incrementCounterByResult() {
		// given
		NotificationType type = NotificationType.ROLE_CHANGE;

		// when
		notificationMetrics.recordRealtimePublish(type, "success");
		notificationMetrics.recordRealtimePublish(type, "failure");

		// then
		double successCount = meterRegistry
			.get("mopl.notification.realtime.publish")
			.tag("type", "role_change")
			.tag("result", "success")
			.counter()
			.count();

		double failureCount = meterRegistry
			.get("mopl.notification.realtime.publish")
			.tag("type", "role_change")
			.tag("result", "failure")
			.counter()
			.count();

		assertEquals(1.0, successCount);
		assertEquals(1.0, failureCount);
	}

	private double getCounter(String metric, String tagKey, String tagValue) {
		return meterRegistry
			.get(metric)
			.tag(tagKey, tagValue)
			.counter()
			.count();
	}

	private double getRealtimePublishCount(String type, String result) {
		return meterRegistry
			.get("mopl.notification.realtime.publish")
			.tag("type", type)
			.tag("result", result)
			.counter()
			.count();
	}
}
