package com.team04.mopl.content.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 애플리케이션 시작 시 Content 채팅 Counter와 Timer의 고정 태그 조합을 초기값 0으로 등록하는 컴포넌트
@Component
public class ContentChatMetricsInitializer {

	private static final List<String> RESULTS = List.of("success", "failure");

	public ContentChatMetricsInitializer(MeterRegistry meterRegistry) {
		RESULTS.forEach(result -> {
			meterRegistry.counter(
				"mopl.content.chat.send",
				"result", result
			);
			meterRegistry.timer(
				"mopl.content.chat.send.duration",
				"result", result
			);
			meterRegistry.counter(
				"mopl.content.chat.publish",
				"result", result
			);
		});

		meterRegistry.counter(
			"mopl.content.chat.rejected",
			"reason", "invalid_format"
		);
	}
}
