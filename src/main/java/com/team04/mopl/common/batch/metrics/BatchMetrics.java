package com.team04.mopl.common.batch.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;

// Batch 실행 결과, 처리 건수, 마지막 성공 시간을 Micrometer로 기록하는 컴포넌트
@Component
public class BatchMetrics {

	// Batch Job 실행 결과별 횟수 Counter에 사용하는 메트릭 이름
	private static final String BATCH_RUN = "mopl.batch.run";

	// Batch Step의 작업 유형별 처리 건수 Counter에 사용하는 메트릭 이름
	private static final String BATCH_ITEMS = "mopl.batch.items";

	// Batch Job별 마지막 성공 시간 TimeGauge에 사용하는 메트릭 이름
	private static final String BATCH_LAST_SUCCESS = "mopl.batch.last.success.timestamp";

	// Counter와 Gauge 등록하고 값을 기록할 때 사용
	private final MeterRegistry meterRegistry;

	// 마지막 성공 시각 저장소
	private final Map<String, AtomicLong> lastSuccessTimestamps = new ConcurrentHashMap<>();

	public BatchMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	// Job이 한 번 끝날 때마다 실행 횟수를 1 증가 시킴
	public void recordRun(String job, String result) {
		meterRegistry.counter(
			BATCH_RUN,
			"batch_job", job,
			"result", result
		).increment();
	}

	// 특정 Step이 처리한 아이템 수를 기록
	public void recordItems(String job, String step, String operation, long count) {
		if (count <= 0) {
			return;
		}

		meterRegistry.counter(
			BATCH_ITEMS,
			"batch_job", job,
			"step", step,
			"operation", operation
		).increment(count);
	}

	// 특정 Batch Job이 마지막으로 성공한 시간을 갱신
	public void updateLastSuccess(String job, long epochSeconds) {
		// Map에 Job 이름이 있는지 확인
		AtomicLong timestamp = lastSuccessTimestamps.computeIfAbsent(
			job,
			jobName -> registerLastSuccessGauge(jobName)
		);

		// 최신 성공 시각으로 교체
		timestamp.set(epochSeconds);
	}

	// 특정 Job의 마지막 성공 시간 Gauge를 처음 한 번 등록하는 메서드
	private AtomicLong registerLastSuccessGauge(String job) {
		AtomicLong timestamp = new AtomicLong(0);

		// timestamp:
		TimeGauge.builder(
				BATCH_LAST_SUCCESS,
				timestamp,  // Gauge가 관찰할 객체
				TimeUnit.SECONDS, // 단위
				value -> value.get()  // Prometheus가 조회할 때 값을 가져올 방법
			)
			.description("Last successful batch execution timestamp")
			.tag("batch_job", job)
			.register(meterRegistry); // TimeGauge를 MeterRegistry에 등록

		return timestamp;
	}
}
