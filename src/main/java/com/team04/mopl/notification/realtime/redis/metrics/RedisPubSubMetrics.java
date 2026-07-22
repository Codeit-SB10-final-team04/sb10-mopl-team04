package com.team04.mopl.notification.realtime.redis.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

// Notification Redis Pub/Sub 발행/수신/처리 결과와 시간을 Micrometer로 기록하는 컴포넌트
@Component
public class RedisPubSubMetrics {

	private static final List<String> RESULTS = List.of(
		"success",
		"failure"
	);

	// Redis 메시지 Publish 성공/실패 횟수와 처리 시간 Timer에 사용하는 메트릭 이름
	private static final String REDIS_PUBSUB_PUBLISH = "mopl.redis.pubsub.publish";

	// Redis Subscriber가 메시지를 수신한 횟수 Counter에 사용하는 메트릭 이름
	private static final String REDIS_PUBSUB_RECEIVE = "mopl.redis.pubsub.receive";

	// Redis 메시지 역직렬화 실패 횟수 Counter에 사용하는 메트릭 이름
	private static final String REDIS_PUBSUB_DESERIALIZATION_FAILURE = "mopl.redis.pubsub.deserialization.failure";

	// Redis Subscriber의 메시지 처리 성공/실패와 처리 시간 Timer에 사용하는 메트릭 이름
	private static final String REDIS_PUBSUB_PROCESS = "mopl.redis.pubsub.process";

	private final MeterRegistry meterRegistry;

	public RedisPubSubMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		registerMeters();
	}

	private void registerMeters() {
		meterRegistry.counter(REDIS_PUBSUB_RECEIVE);
		meterRegistry.counter(REDIS_PUBSUB_DESERIALIZATION_FAILURE);

		RESULTS.forEach(result -> {
			meterRegistry.timer(
				REDIS_PUBSUB_PUBLISH,
				"result", result
			);
			meterRegistry.timer(
				REDIS_PUBSUB_PROCESS,
				"result", result
			);
		});
	}

	// Redis Publish 또는 Subscriber 처리 시간을 측정하기 위한 Timer Sample 시작
	public Timer.Sample startTimer() {
		return Timer.start(meterRegistry);
	}

	// Redis 메시지 Publish 성공/실패 결과와 처리 시간을 기록
	public void recordPublish(Timer.Sample sample, String result) {
		sample.stop(
			meterRegistry.timer(
				REDIS_PUBSUB_PUBLISH,
				"result", result
			)
		);
	}

	// Redis Subscriber가 메시지를 수신할 때마다 수신 횟수를 1 증가
	public void recordReceive() {
		meterRegistry.counter(
			REDIS_PUBSUB_RECEIVE
		).increment();
	}

	// Redis 메시지 역직렬화에 실패할 때마다 실패 횟수를 1 증가
	public void recordDeserializationFailure() {
		meterRegistry.counter(
			REDIS_PUBSUB_DESERIALIZATION_FAILURE
		).increment();
	}

	// Redis Subscriber의 메시지 성공/실패와 처리 시간을 기록
	public void recordProcess(Timer.Sample sample, String result) {
		sample.stop(
			meterRegistry.timer(
				REDIS_PUBSUB_PROCESS,
				"result", result
			)
		);
	}
}
