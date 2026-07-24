package com.team04.mopl.common.stomp.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 애플리케이션 시작 시 WebSocket/STOMP Counter의 고정 태그 조합을 초기값 0으로 등록하는 컴포넌트
@Component
public class WebSocketStompMetricsInitializer {

	private static final List<String> RESULTS = List.of("success", "failure");
	private static final List<String> CONNECTION_STATUSES = List.of("connected", "disconnected");
	private static final List<String> AUTHENTICATION_FAILURE_REASONS = List.of(
		"invalid_session",
		"invalid_token",
		"unknown"
	);
	private static final List<String> DESTINATION_TYPES = List.of(
		"dm",
		"content_chat",
		"watching_session",
		"other"
	);
	private static final List<String> REJECTION_REASONS = List.of(
		"not_watching",
		"unauthorized"
	);

	public WebSocketStompMetricsInitializer(MeterRegistry meterRegistry) {
		CONNECTION_STATUSES.forEach(status ->
			meterRegistry.counter(
				"mopl.websocket.connection",
				"status", status
			)
		);

		RESULTS.forEach(result ->
			meterRegistry.counter(
				"mopl.stomp.authentication",
				"result", result
			)
		);

		AUTHENTICATION_FAILURE_REASONS.forEach(reason ->
			meterRegistry.counter(
				"mopl.stomp.authentication.failure",
				"reason", reason
			)
		);

		DESTINATION_TYPES.forEach(destinationType ->
			RESULTS.forEach(result ->
				meterRegistry.counter(
					"mopl.stomp.subscription",
					"destination_type", destinationType,
					"result", result
				)
			)
		);

		REJECTION_REASONS.forEach(reason ->
			meterRegistry.counter(
				"mopl.stomp.rejected",
				"operation", "send",
				"reason", reason
			)
		);
	}
}
