package com.team04.mopl.common.batch.metrics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BatchMetricsInitializerTest {

	private MeterRegistry meterRegistry;
	private BatchMetricsInitializer batchMetricsInitializer;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		BatchMetrics batchMetrics = new BatchMetrics(meterRegistry);

		Job firstJob = mock(Job.class);
		Job secondJob = mock(Job.class);

		when(firstJob.getName()).thenReturn("notificationHardDeleteJob");
		when(secondJob.getName()).thenReturn("tmdbDailyCollectJob");

		batchMetricsInitializer = new BatchMetricsInitializer(
			List.of(firstJob, secondJob),
			batchMetrics
		);
	}

	@Test
	@DisplayName("애플리케이션 시작 시 모든 Batch Job의 실행 결과 Counter를 등록한다.")
	void initialize_registerRunCountersForAllJobsAndResults() {
		// when
		batchMetricsInitializer.initialize();

		// then
		assertAll(
			() -> assertCounterCount("notificationHardDeleteJob", "success", 0.0),
			() -> assertCounterCount("notificationHardDeleteJob", "failure", 0.0),
			() -> assertCounterCount("notificationHardDeleteJob", "stopped", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "success", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "failure", 0.0),
			() -> assertCounterCount("tmdbDailyCollectJob", "stopped", 0.0)
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
}
