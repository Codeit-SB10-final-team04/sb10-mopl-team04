package com.team04.mopl.notification.realtime.redis.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RedisPubSubMetricsTest {

	private MeterRegistry meterRegistry;
	private RedisPubSubMetrics redisPubSubMetrics;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		redisPubSubMetrics = new RedisPubSubMetrics(meterRegistry);
	}

	@Test
	@DisplayName("Redis Publish 처리 시간과 횟수를 성공과 실패 결과별로 기록한다.")
	void recordPublish_recordTimerByResult() {
		// given
		Timer.Sample successSample = redisPubSubMetrics.startTimer();
		Timer.Sample failureSample = redisPubSubMetrics.startTimer();

		// when
		redisPubSubMetrics.recordPublish(successSample, "success");
		redisPubSubMetrics.recordPublish(failureSample, "failure");

		// then
		Timer successTimer = meterRegistry
			.get("mopl.redis.pubsub.publish")
			.tag("result", "success")
			.timer();
		Timer failureTimer = meterRegistry
			.get("mopl.redis.pubsub.publish")
			.tag("result", "failure")
			.timer();

		assertEquals(1L, successTimer.count());
		assertEquals(1L, failureTimer.count());
	}

	@Test
	@DisplayName("Redis Subscriber가 메시지를 수신하면 수신 Counter를 1 증가시킨다.")
	void recordReceive_incrementCounter() {
		// when
		redisPubSubMetrics.recordReceive();

		// then
		double count = meterRegistry
			.get("mopl.redis.pubsub.receive")
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("Redis 메시지 역직렬화 실패를 기록하면 실패 Counter를 1 증가시킨다.")
	void recordDeserializationFailure_incrementCounter() {
		// when
		redisPubSubMetrics.recordDeserializationFailure();

		// then
		double count = meterRegistry
			.get("mopl.redis.pubsub.deserialization.failure")
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("Redis Subscriber 처리 시간과 횟수를 성공과 실패 결과별로 기록한다.")
	void recordProcess_recordTimerByResult() {
		// given
		Timer.Sample successSample = redisPubSubMetrics.startTimer();
		Timer.Sample failureSample = redisPubSubMetrics.startTimer();

		// when
		redisPubSubMetrics.recordProcess(successSample, "success");
		redisPubSubMetrics.recordProcess(failureSample, "failure");

		// then
		Timer successTimer = meterRegistry
			.get("mopl.redis.pubsub.process")
			.tag("result", "success")
			.timer();
		Timer failureTimer = meterRegistry
			.get("mopl.redis.pubsub.process")
			.tag("result", "failure")
			.timer();

		assertEquals(1L, successTimer.count());
		assertEquals(1L, failureTimer.count());
	}
}
