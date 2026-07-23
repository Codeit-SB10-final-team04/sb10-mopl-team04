package com.team04.mopl.common.stomp.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WebSocketStompMetricsInitializerTest {

	private MeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		new WebSocketStompMetricsInitializer(meterRegistry);
	}

	@Test
	@DisplayName("생성 시 WebSocket/STOMP Counter의 고정 태그 조합을 초기값 0으로 등록한다.")
	void constructor_registerFixedCounterTagsWithZeroCount() {
		List.of("connected", "disconnected").forEach(status ->
			assertCounterZero("mopl.websocket.connection", "status", status)
		);

		List.of("success", "failure").forEach(result ->
			assertCounterZero("mopl.stomp.authentication", "result", result)
		);

		List.of("invalid_session", "invalid_token", "unknown").forEach(reason ->
			assertCounterZero("mopl.stomp.authentication.failure", "reason", reason)
		);

		List.of("dm", "content_chat", "watching_session", "other")
			.forEach(destinationType ->
				List.of("success", "failure").forEach(result ->
					assertCounterZero(
						"mopl.stomp.subscription",
						"destination_type", destinationType,
						"result", result
					)
				)
			);

		List.of("not_watching", "unauthorized").forEach(reason ->
			assertCounterZero(
				"mopl.stomp.rejected",
				"operation", "send",
				"reason", reason
			)
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
}
