package com.team04.mopl.common.redis.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 애플리케이션 시작 시 도메인 Redis 동기화 Counter와 Timer를 초기값 0으로 등록하는 컴포넌트
@Component
public class DomainRedisSyncMetricsInitializer {

	private static final List<String> RESULTS = List.of("success", "failure");

	public DomainRedisSyncMetricsInitializer(MeterRegistry meterRegistry) {
		registerMeters(meterRegistry, "mopl.dm.redis.sync", List.of("create", "read"));
		registerMeters(meterRegistry, "mopl.conversation.redis.sync", List.of("create"));
		registerMeters(meterRegistry, "mopl.follow.redis.sync", List.of("create", "delete"));
	}

	private void registerMeters(
		MeterRegistry meterRegistry,
		String metricPrefix,
		List<String> operations
	) {
		operations.forEach(operation -> {
			RESULTS.forEach(result -> {
				meterRegistry.counter(
					metricPrefix,
					"operation", operation,
					"result", result
				);
				meterRegistry.timer(
					metricPrefix + ".duration",
					"operation", operation,
					"result", result
				);
				meterRegistry.counter(
					metricPrefix + ".dlq.publish",
					"operation", operation,
					"result", result
				);
			});

			meterRegistry.counter(
				metricPrefix + ".retry",
				"operation", operation
			);
			meterRegistry.counter(
				metricPrefix + ".final.failure",
				"operation", operation
			);
		});
	}
}
