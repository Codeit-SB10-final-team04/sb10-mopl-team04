package com.team04.mopl.common.redis.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class DomainRedisSyncMetricsInitializerTest {

	private MeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		new DomainRedisSyncMetricsInitializer(meterRegistry);
	}

	@Test
	@DisplayName("생성 시 도메인 Redis 동기화 Counter와 Timer의 고정 태그 조합을 초기값 0으로 등록한다.")
	void constructor_registerFixedMetersWithZeroCount() {
		assertDomainMeters("mopl.dm.redis.sync", List.of("create", "read"));
		assertDomainMeters("mopl.conversation.redis.sync", List.of("create"));
		assertDomainMeters("mopl.follow.redis.sync", List.of("create", "delete"));
	}

	private void assertDomainMeters(String metricPrefix, List<String> operations) {
		operations.forEach(operation -> {
			List.of("success", "failure").forEach(result -> {
				assertCounterZero(
					metricPrefix,
					"operation", operation,
					"result", result
				);
				assertTimerZero(
					metricPrefix + ".duration",
					"operation", operation,
					"result", result
				);
				assertCounterZero(
					metricPrefix + ".dlq.publish",
					"operation", operation,
					"result", result
				);
			});

			assertCounterZero(metricPrefix + ".retry", "operation", operation);
			assertCounterZero(metricPrefix + ".final.failure", "operation", operation);
		});
	}

	private void assertCounterZero(String metricName, String... tags) {
		double count = meterRegistry
			.get(metricName)
			.tags(tags)
			.counter()
			.count();

		assertEquals(0.0, count);
	}

	private void assertTimerZero(String metricName, String... tags) {
		Timer timer = meterRegistry
			.get(metricName)
			.tags(tags)
			.timer();

		assertAll(
			() -> assertEquals(0L, timer.count()),
			() -> assertEquals(0.0, timer.totalTime(TimeUnit.SECONDS))
		);
	}
}
