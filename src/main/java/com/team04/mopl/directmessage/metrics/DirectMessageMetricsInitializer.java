package com.team04.mopl.directmessage.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 애플리케이션 시작 시 DM Counter와 Timer의 고정 태그 조합을 초기값 0으로 등록하는 컴포넌트
@Component
public class DirectMessageMetricsInitializer {

	private static final List<String> RESULTS = List.of("success", "failure");

	public DirectMessageMetricsInitializer(MeterRegistry meterRegistry) {
		RESULTS.forEach(result -> {
			meterRegistry.counter(
				"mopl.dm.create",
				"result", result
			);
			meterRegistry.timer(
				"mopl.dm.create.duration",
				"result", result
			);
			meterRegistry.counter(
				"mopl.dm.broadcast",
				"result", result
			);
			meterRegistry.timer(
				"mopl.dm.broadcast.duration",
				"result", result
			);
			meterRegistry.counter(
				"mopl.dm.read",
				"result", result
			);
		});

		meterRegistry.counter(
			"mopl.dm.rejected",
			"reason", "invalid_format"
		);
		meterRegistry.counter("mopl.dm.read.messages");
		meterRegistry.counter("mopl.dm.read.no.change");
	}
}
