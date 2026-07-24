package com.team04.mopl.common.batch.metrics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

@ExtendWith(MockitoExtension.class)
class MoplBatchStepMetricsListenerTest {

	@Mock
	private BatchMetrics batchMetrics;

	@InjectMocks
	private MoplBatchStepMetricsListener moplBatchStepMetricsListener;

	@Test
	@DisplayName("Step이 종료되면 read, write, filter, skip 건수를 기록한다.")
	void afterStep_recordItemCounts_whenStepCompleted() {
		// given
		StepExecution stepExecution = Mockito.mock(StepExecution.class);
		String stepName = "tmdbDailyCollectStep";
		JobExecution jobExecution = Mockito.mock(JobExecution.class);
		String jobName = "tmdbDailyCollectJob";
		JobInstance jobInstance = new JobInstance(1L, jobName);

		when(stepExecution.getJobExecution())
			.thenReturn(jobExecution);
		when(jobExecution.getJobInstance())
			.thenReturn(jobInstance);
		when(stepExecution.getStepName())
			.thenReturn(stepName);
		when(stepExecution.getReadCount())
			.thenReturn(100L);
		when(stepExecution.getWriteCount())
			.thenReturn(90L);
		when(stepExecution.getFilterCount())
			.thenReturn(10L);
		when(stepExecution.getReadSkipCount())
			.thenReturn(1L);
		when(stepExecution.getProcessSkipCount())
			.thenReturn(2L);
		when(stepExecution.getWriteSkipCount())
			.thenReturn(3L);

		// when
		ExitStatus result = moplBatchStepMetricsListener.afterStep(stepExecution);

		// then
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"read",
			100L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"write",
			90L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"filter",
			10L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"skip",
			6L
		);

		assertNull(result);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"contentHardDeleteStep",
		"reviewHardDeleteStep",
		"playlistHardDeleteStep",
		"notificationHardDeleteStep"
	})
	@DisplayName("물리 삭제 Step이 종료되면 writeCount를 delete 건수로 기록한다.")
	void afterStep_recordDeleteCount_whenHardDeleteStep(String stepName) {
		// given
		StepExecution stepExecution = Mockito.mock(StepExecution.class);
		JobExecution jobExecution = Mockito.mock(JobExecution.class);
		String jobName = "hardDeleteJob";
		JobInstance jobInstance = new JobInstance(1L, jobName);

		when(stepExecution.getJobExecution())
			.thenReturn(jobExecution);
		when(jobExecution.getJobInstance())
			.thenReturn(jobInstance);
		when(stepExecution.getStepName())
			.thenReturn(stepName);
		when(stepExecution.getReadCount())
			.thenReturn(100L);
		when(stepExecution.getWriteCount())
			.thenReturn(90L);
		when(stepExecution.getFilterCount())
			.thenReturn(10L);
		when(stepExecution.getReadSkipCount())
			.thenReturn(1L);
		when(stepExecution.getProcessSkipCount())
			.thenReturn(2L);
		when(stepExecution.getWriteSkipCount())
			.thenReturn(3L);

		// when
		ExitStatus result = moplBatchStepMetricsListener.afterStep(stepExecution);

		// then
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"read",
			100L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"delete",
			90L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"filter",
			10L
		);
		verify(batchMetrics).recordItems(
			jobName,
			stepName,
			"skip",
			6L
		);
	}
}