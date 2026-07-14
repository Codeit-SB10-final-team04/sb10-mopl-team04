package com.team04.mopl.content.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class DataCollectSchedulerTest {

	@Mock private com.team04.mopl.common.redis.DistributedLock distributedLock;
	@Mock private JobLauncher jobLauncher;
	@Mock private JobExplorer jobExplorer;
	@Mock private Job sportsDataCollectJob;
	@Mock private Job tmdbInitialCollectJob;
	@Mock private Job tmdbDailyCollectJob;

	private DataCollectScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new DataCollectScheduler(
			distributedLock, jobLauncher, jobExplorer, sportsDataCollectJob, tmdbInitialCollectJob, tmdbDailyCollectJob);
	}

	// ========== isSeasonSkippable ==========

	@Test
	@DisplayName("해당 시즌이 COMPLETED 상태면 skip 대상이다")
	void isSeasonSkippable_returnsTrue_whenSeasonIsCompleted() {
		// given
		JobInstance instance = mock(JobInstance.class);
		JobExecution execution = mock(JobExecution.class);
		JobParameters params = new JobParametersBuilder()
			.addString("season", "2024-2025")
			.toJobParameters();

		when(jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE))
			.thenReturn(List.of(instance));
		when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(execution));
		when(execution.getJobParameters()).thenReturn(params);
		when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);

		// when
		boolean result = scheduler.isSeasonSkippable("2024-2025");

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("해당 시즌이 STARTED(실행 중) 상태면 skip 대상이다")
	void isSeasonSkippable_returnsTrue_whenSeasonIsRunning() {
		// given
		JobInstance instance = mock(JobInstance.class);
		JobExecution execution = mock(JobExecution.class);
		JobParameters params = new JobParametersBuilder()
			.addString("season", "2025-2026")
			.toJobParameters();

		when(jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE))
			.thenReturn(List.of(instance));
		when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(execution));
		when(execution.getJobParameters()).thenReturn(params);
		when(execution.getStatus()).thenReturn(BatchStatus.STARTED);

		// when
		boolean result = scheduler.isSeasonSkippable("2025-2026");

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("해당 시즌이 STARTING 상태면 skip 대상이다")
	void isSeasonSkippable_returnsTrue_whenSeasonIsStarting() {
		// given
		JobInstance instance = mock(JobInstance.class);
		JobExecution execution = mock(JobExecution.class);
		JobParameters params = new JobParametersBuilder()
			.addString("season", "2025-2026")
			.toJobParameters();

		when(jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE))
			.thenReturn(List.of(instance));
		when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(execution));
		when(execution.getJobParameters()).thenReturn(params);
		when(execution.getStatus()).thenReturn(BatchStatus.STARTING);

		// when
		boolean result = scheduler.isSeasonSkippable("2025-2026");

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("해당 시즌 이력이 없으면 skip 대상이 아니다")
	void isSeasonSkippable_returnsFalse_whenNoHistory() {
		// given
		when(jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE))
			.thenReturn(List.of());

		// when
		boolean result = scheduler.isSeasonSkippable("2024-2025");

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("다른 시즌이 COMPLETED여도 해당 시즌은 skip 대상이 아니다")
	void isSeasonSkippable_returnsFalse_whenDifferentSeasonIsCompleted() {
		// given
		JobInstance instance = mock(JobInstance.class);
		JobExecution execution = mock(JobExecution.class);
		JobParameters params = new JobParametersBuilder()
			.addString("season", "2023-2024") // 다른 시즌
			.toJobParameters();

		when(jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE))
			.thenReturn(List.of(instance));
		when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(execution));
		when(execution.getJobParameters()).thenReturn(params);
		// getStatus() 호출 안 됨 - 시즌 필터에서 제외되므로 stubbing 불필요

		// when
		boolean result = scheduler.isSeasonSkippable("2025-2026");

		// then
		assertThat(result).isFalse();
	}

	// ========== runSportsJobWithLock ==========

	@Test
	@DisplayName("분산 락 획득 성공 시 sportsDataCollectJob을 실행한다")
	void runSportsJobWithLock_runsJob_whenLockAcquired() throws Exception {
		// given: 분산 락 획득 성공 → task 실행
		when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(), any()))
			.thenAnswer(inv -> {
				Runnable task = inv.getArgument(3);
				task.run();
				return true;
			});
		JobExecution jobExecution = mock(JobExecution.class);
		when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
		when(jobExecution.getJobId()).thenReturn(1L);
		when(jobLauncher.run(eq(sportsDataCollectJob), any())).thenReturn(jobExecution);

		// when
		scheduler.runSportsJobWithLock("2025-2026");

		// then
		verify(jobLauncher).run(eq(sportsDataCollectJob), any());
	}

	@Test
	@DisplayName("분산 락 획득 실패 시 Job을 실행하지 않고 skip한다")
	void runSportsJobWithLock_skipsJob_whenLockAlreadyHeld() throws Exception {
		// given: 분산 락 획득 실패
		when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(), any()))
			.thenReturn(false);

		// when
		scheduler.runSportsJobWithLock("2025-2026");

		// then
		verify(jobLauncher, never()).run(any(), any());
	}
}
