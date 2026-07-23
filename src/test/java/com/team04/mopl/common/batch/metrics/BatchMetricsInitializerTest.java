package com.team04.mopl.common.batch.metrics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.SimpleJob;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BatchMetricsInitializerTest {

	private MeterRegistry meterRegistry;
	private BatchMetricsInitializer batchMetricsInitializer;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		BatchMetrics batchMetrics = new BatchMetrics(meterRegistry);

		Step firstStep = mock(Step.class);
		Step secondStep = mock(Step.class);

		when(firstStep.getName()).thenReturn("notificationHardDeleteStep");
		when(secondStep.getName()).thenReturn("tmdbDailyCollectStep");

		SimpleJob firstJob = new SimpleJob("notificationHardDeleteJob");
		firstJob.setSteps(List.of(firstStep));

		SimpleJob secondJob = new SimpleJob("tmdbDailyCollectJob");
		secondJob.setSteps(List.of(secondStep));

		batchMetricsInitializer = new BatchMetricsInitializer(
			List.of(firstJob, secondJob),
			batchMetrics
		);
	}

	@Test
	@DisplayName("애플리케이션 시작 시 모든 Batch Job 실행 결과와 Step 처리 건수 Counter를 등록한다.")
	void initialize_registerCountersForAllJobsAndSteps() {
		// when
		batchMetricsInitializer.initialize();

		// then
		assertAll(
			() -> assertCounterCount("notificationHardDeleteJob", "success", 0.0),
			() -> assertCounterCount("notificationHardDeleteJob", "failure", 0.0),
			() -> assertCounterCount("notificationHardDeleteJob", "stopped", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "success", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "failure", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "stopped", 0.0),
			() -> assertItemCounterCount(
				"notificationHardDeleteJob", "notificationHardDeleteStep", "read", 0.0),
			() -> assertItemCounterCount(
				"notificationHardDeleteJob", "notificationHardDeleteStep", "delete", 0.0),
			() -> assertItemCounterCount(
				"notificationHardDeleteJob", "notificationHardDeleteStep", "filter", 0.0),
			() -> assertItemCounterCount(
				"notificationHardDeleteJob", "notificationHardDeleteStep", "skip", 0.0),
			() -> assertItemCounterCount(
				"tmdbDailyCollectJob", "tmdbDailyCollectStep", "read", 0.0),
			() -> assertItemCounterCount(
				"tmdbDailyCollectJob", "tmdbDailyCollectStep", "write", 0.0),
			() -> assertItemCounterCount(
				"tmdbDailyCollectJob", "tmdbDailyCollectStep", "filter", 0.0),
			() -> assertItemCounterCount(
				"tmdbDailyCollectJob", "tmdbDailyCollectStep", "skip", 0.0)
		);
	}

	private void assertCounterCount(String job, String result, double expectedCount) {
		double actualCount = meterRegistry
			.get("mopl.batch.run")
			.tag("batch_job", job)
			.tag("result", result)
			.counter()
			.count();

		assertEquals(expectedCount, actualCount);
	}

	private void assertItemCounterCount(
		String job,
		String step,
		String operation,
		double expectedCount
	) {
		double actualCount = meterRegistry
			.get("mopl.batch.items")
			.tag("batch_job", job)
			.tag("step", step)
			.tag("operation", operation)
			.counter()
			.count();

		assertEquals(expectedCount, actualCount);
	}
}
