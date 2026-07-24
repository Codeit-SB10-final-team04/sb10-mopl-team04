package com.team04.mopl.common.batch.metrics;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;

@ExtendWith(MockitoExtension.class)
class MoplBatchJobMetricsListenerTest {

	@Mock
	private BatchMetrics batchMetrics;

	@InjectMocks
	private MoplBatchJobMetricsListener moplBatchJobMetricsListener;

	@Test
	@DisplayName("Batch Job이 COMPLETED 상태이면 success 횟수와 마지막 성공 시각을 기록한다.")
	void afterJob_recordSuccessAndLastSuccess_whenCompleted() {
		// given
		JobExecution jobExecution = Mockito.mock(JobExecution.class);
		JobInstance jobInstance = new JobInstance(1L, "tmdbDailyCollectJob");

		when(jobExecution.getJobInstance())
			.thenReturn(jobInstance);
		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.COMPLETED);

		// when
		moplBatchJobMetricsListener.afterJob(jobExecution);

		// then
		verify(batchMetrics).recordRun(
			"tmdbDailyCollectJob",
			"success"
		);
		verify(batchMetrics).updateLastSuccess(
			eq("tmdbDailyCollectJob"),
			anyLong()
		);
	}

	@Test
	@DisplayName("Batch Job이 STOPPED 상태이면 stopped 횟수만 기록한다.")
	void afterJob_recordStopped_whenStopped() {
		// given
		JobExecution jobExecution = Mockito.mock(JobExecution.class);
		JobInstance jobInstance = new JobInstance(1L, "tmdbDailyCollectJob");

		when(jobExecution.getJobInstance())
			.thenReturn(jobInstance);
		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.STOPPED);

		// when
		moplBatchJobMetricsListener.afterJob(jobExecution);

		// then
		verify(batchMetrics).recordRun(
			"tmdbDailyCollectJob",
			"stopped"
		);
		verify(batchMetrics, never()).updateLastSuccess(
			anyString(),
			anyLong()
		);
	}

	@Test
	@DisplayName("Batch Job이 FAILED 상태이면 failure 횟수만 기록한다.")
	void afterJob_recordFailure_whenFailed() {
		// given
		JobExecution jobExecution = Mockito.mock(JobExecution.class);
		JobInstance jobInstance = new JobInstance(1L, "tmdbDailyCollectJob");

		when(jobExecution.getJobInstance())
			.thenReturn(jobInstance);
		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.FAILED);

		// when
		moplBatchJobMetricsListener.afterJob(jobExecution);

		// then
		verify(batchMetrics).recordRun(
			"tmdbDailyCollectJob",
			"failure"
		);
		verify(batchMetrics, never()).updateLastSuccess(
			anyString(),
			anyLong()
		);
	}
}