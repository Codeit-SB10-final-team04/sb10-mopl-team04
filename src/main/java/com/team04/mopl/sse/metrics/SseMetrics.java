package com.team04.mopl.sse.metrics;

import org.springframework.stereotype.Component;

import com.team04.mopl.sse.repository.SseEmitterRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

// SSE 연결 상태와 이벤트 전송 결과를 Micrometer로 기록하는 컴포넌트
@Component
public class SseMetrics {

	// 현재 인스턴스에 등록된 전체 Emitter 수 Gauge에 사용하는 메트릭 이름
	private static final String SSE_CONNECTIONS = "mopl.sse.connections";

	// SSE 연결 및 종료 상태 Counter에 사용하는 메트릭 이름
	private static final String SSE_LIFECYCLE = "mopl.sse.lifecycle";

	// SSE 이벤트 전송 결과 Counter에 사용하는 메트릭 이름
	private static final String SSE_SEND = "mopl.sse.send";

	private final MeterRegistry meterRegistry;

	public SseMetrics(MeterRegistry meterRegistry, SseEmitterRepository sseEmitterRepository) {
		this.meterRegistry = meterRegistry;

		// 현재 인스턴스에 등록된 전체 Emitter 수
		Gauge.builder(
				SSE_CONNECTIONS,
				sseEmitterRepository,
				repository -> repository.count()
			)
			.description("Current SSE connection count")
			.register(meterRegistry);
	}

	// SSE 연결 및 종료 상태 발생 횟수를 상태별로 기록
	public void recordLifecycle(String state) {
		meterRegistry.counter(
			SSE_LIFECYCLE,
			"state", state
		).increment();
	}

	// SSE 이벤트 전송 성공 및 실패 횟수를 이벤트별로 기록
	public void recordSend(String eventName, String result) {
		meterRegistry.counter(
			SSE_SEND,
			"event", toEventTag(eventName),
			"result", result
		).increment();
	}

	private String toEventTag(String eventName) {
		return eventName.replace("-", "_");
	}
}
