package com.team04.mopl.sse.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.sse.event.SseEventNames;
import com.team04.mopl.sse.repository.SseEmitterRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SseMetricsTest {

	private MeterRegistry meterRegistry;
	private SseEmitterRepository sseEmitterRepository;
	private SseMetrics sseMetrics;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		sseEmitterRepository = new SseEmitterRepository();
		sseMetrics = new SseMetrics(meterRegistry, sseEmitterRepository);
	}

	@Test
	@DisplayName("생성 시 고정된 SSE Counter 태그 조합을 초기값 0으로 등록한다.")
	void constructor_registerFixedCounterTagsWithZeroCount() {
		assertAll(
			() -> assertEquals(0.0, getLifecycleCount("connected")),
			() -> assertEquals(0.0, getLifecycleCount("completed")),
			() -> assertEquals(0.0, getLifecycleCount("timeout")),
			() -> assertEquals(0.0, getLifecycleCount("error")),
			() -> assertEquals(0.0, getSendCount("notifications", "success")),
			() -> assertEquals(0.0, getSendCount("notifications", "failure")),
			() -> assertEquals(0.0, getSendCount("direct_messages", "success")),
			() -> assertEquals(0.0, getSendCount("direct_messages", "failure"))
		);
	}

	@Test
	@DisplayName("SSE 연결 수 Gauge는 Repository의 현재 Emitter 수를 동적으로 반영한다.")
	void connectionsGauge_reflectRepositoryStateDynamically() {
		// given
		UUID receiverId = UUID.randomUUID();
		SseEmitter sseEmitter1 = new SseEmitter();
		SseEmitter sseEmitter2 = new SseEmitter();

		// when, then
		assertEquals(0.0, getConnectionCount());

		sseEmitterRepository.add(receiverId, sseEmitter1);
		assertEquals(1.0, getConnectionCount());

		sseEmitterRepository.add(receiverId, sseEmitter2);
		assertEquals(2.0, getConnectionCount());

		sseEmitterRepository.remove(receiverId, sseEmitter1);
		assertEquals(1.0, getConnectionCount());

		sseEmitterRepository.remove(receiverId, sseEmitter2);
		assertEquals(0.0, getConnectionCount());
	}

	@Test
	@DisplayName("SSE 연결 및 종료 상태를 lifecycle 상태별 Counter로 분리해 기록한다.")
	void recordLifecycle_incrementCounterByState() {
		// when
		sseMetrics.recordLifecycle("connected");
		sseMetrics.recordLifecycle("completed");
		sseMetrics.recordLifecycle("timeout");
		sseMetrics.recordLifecycle("error");

		// then
		assertEquals(1.0, getLifecycleCount("connected"));
		assertEquals(1.0, getLifecycleCount("completed"));
		assertEquals(1.0, getLifecycleCount("timeout"));
		assertEquals(1.0, getLifecycleCount("error"));
	}

	@Test
	@DisplayName("알림 SSE 이벤트 전송 성공과 실패를 결과별 Counter로 분리해 기록한다.")
	void recordSend_incrementCounterByResult() {
		// when
		sseMetrics.recordSend(SseEventNames.NOTIFICATIONS, "success");
		sseMetrics.recordSend(SseEventNames.NOTIFICATIONS, "failure");

		// then
		assertEquals(1.0, getSendCount("notifications", "success"));
		assertEquals(1.0, getSendCount("notifications", "failure"));
	}

	@Test
	@DisplayName("DM SSE 이벤트 이름의 하이픈을 밑줄로 변환해 태그에 기록한다.")
	void recordSend_convertEventNameToSnakeCase() {
		// when
		sseMetrics.recordSend(SseEventNames.DIRECT_MESSAGES, "success");

		// then
		assertEquals(1.0, getSendCount("direct_messages", "success"));
	}

	private double getConnectionCount() {
		return meterRegistry
			.get("mopl.sse.connections")
			.gauge()
			.value();
	}

	private double getLifecycleCount(String state) {
		return meterRegistry
			.get("mopl.sse.lifecycle")
			.tag("state", state)
			.counter()
			.count();
	}

	private double getSendCount(String event, String result) {
		return meterRegistry
			.get("mopl.sse.send")
			.tag("event", event)
			.tag("result", result)
			.counter()
			.count();
	}
}
