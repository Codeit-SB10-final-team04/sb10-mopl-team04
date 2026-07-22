package com.team04.mopl.watching.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WatchingMetricsInitializerTest {

	private MeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		new WatchingMetricsInitializer(meterRegistry);
	}

	@Test
	@DisplayName("생성 시 시청 세션 Counter와 Timer의 고정 태그 조합을 초기값 0으로 등록한다.")
	void constructor_registerFixedMetersWithZeroCount() {
		List.of("join", "leave").forEach(operation ->
			List.of("success", "failure").forEach(result -> {
				assertCounterZero(
					"mopl.watching.session.change",
					"operation", operation,
					"result", result
				);
				assertTimerZero(
					"mopl.watching.session.change.duration",
					"operation", operation,
					"result", result
				);
			})
		);

		List.of("watching:session:*", "watching:user-sessions:*").forEach(pattern ->
			assertCounterZero("mopl.watching.scan.error", "pattern", pattern)
		);
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
