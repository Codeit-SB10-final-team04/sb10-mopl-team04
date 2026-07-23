package com.team04.mopl.common.batch.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BatchMetricsTest {

	private MeterRegistry meterRegistry;
	private BatchMetrics batchMetrics;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		batchMetrics = new BatchMetrics(meterRegistry);
	}

	@Test
	@DisplayName("Batch Job 실행 결과 Counter를 초기값 0으로 등록한다.")
	void registerRun_registerCounterWithZeroCount() {
		String job = "tmdbDailyCollectJob";
		String result = "success";

		// when
		batchMetrics.registerRun(job, result);

		// then
		double count = meterRegistry
			.get("mopl.batch.run")
			.tag("batch_job", job)
			.tag("result", result)
			.counter()
			.count();

		assertEquals(0.0, count);
	}

	@Test
	@DisplayName("실행 결과가 success이면 Batch Job 실행 횟수를 1 증가시킨다.")
	void recordRun_incrementCounter_whenResultIsSuccess() {
		String job = "tmdbDailyCollectJob";
		String result = "success";

		// when
		batchMetrics.recordRun(job, result);

		// then
		double count = meterRegistry
			.get("mopl.batch.run")
			.tag("batch_job", job)
			.tag("result", result)
			.counter()
			.count();

		assertEquals(1.0, count);
	}

	@Test
	@DisplayName("처리 건수가 양수이면 Step 처리 건수 Counter를 해당 값만큼 증가시킨다.")
	void recordItems_incrementCounter_whenCountIsPositive() {
		String job = "tmdbDailyCollectJob";
		String step = "tmdbDailyCollectStep";
		String operation = "read";

		// when
		batchMetrics.recordItems(job, step, operation, 50);

		// then
		double count = meterRegistry
			.get("mopl.batch.items")
			.tag("batch_job", job)
			.tag("step", step)
			.tag("operation", operation)
			.counter()
			.count();

		assertEquals(50.0, count);
	}

	@Test
	@DisplayName("처리 건수가 0이면 Step 처리 건수 Counter를 0으로 등록한다.")
	void recordItems_registerCounterWithoutIncrement_whenCountIsZero() {
		// given
		String job = "tmdbDailyCollectJob";
		String step = "tmdbDailyCollectStep";
		String operation = "write";

		// when
		batchMetrics.recordItems(job, step, operation, 0);

		// then
		double count = meterRegistry
			.get("mopl.batch.items")
			.tag("batch_job", job)
			.tag("step", step)
			.tag("operation", operation)
			.counter()
			.count();

		assertEquals(0.0, count);
	}

	@Test
	@DisplayName("마지막 성공 시각 Gauge를 전달받은 시각으로 기록한다.")
	void updateLastSuccess_recordTimestamp() {
		String job = "tmdbDailyCollectJob";
		long epochSeconds = 1_721_440_000L;

		// when
		batchMetrics.updateLastSuccess(job, epochSeconds);

		// then
		double actualEpochSeconds = meterRegistry
			.get("mopl.batch.last.success.timestamp")
			.tag("batch_job", job)
			.timeGauge()
			.value(TimeUnit.SECONDS);

		assertEquals(epochSeconds, actualEpochSeconds);
	}
}
