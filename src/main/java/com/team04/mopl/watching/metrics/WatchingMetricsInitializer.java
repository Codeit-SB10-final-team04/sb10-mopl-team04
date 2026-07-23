package com.team04.mopl.watching.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 애플리케이션 시작 시 시청 세션 Counter와 Timer의 고정 태그 조합을 초기값 0으로 등록하는 컴포넌트
@Component
public class WatchingMetricsInitializer {

	private static final List<String> OPERATIONS = List.of("join", "leave");
	private static final List<String> RESULTS = List.of("success", "failure");
	private static final List<String> SCAN_PATTERNS = List.of(
		"watching:session:*",
		"watching:user-sessions:*"
	);

	public WatchingMetricsInitializer(MeterRegistry meterRegistry) {
		OPERATIONS.forEach(operation ->
			RESULTS.forEach(result -> {
				meterRegistry.counter(
					"mopl.watching.session.change",
					"operation", operation,
					"result", result
				);
				meterRegistry.timer(
					"mopl.watching.session.change.duration",
					"operation", operation,
					"result", result
				);
			})
		);

		SCAN_PATTERNS.forEach(pattern ->
			meterRegistry.counter(
				"mopl.watching.scan.error",
				"pattern", pattern
			)
		);
	}
}
