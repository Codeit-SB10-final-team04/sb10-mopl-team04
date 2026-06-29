package com.team04.mopl.content.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DataCollectSchedulerTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private JobExplorer jobExplorer;
    @Mock private Job sportsDataCollectJob;
    @Mock private Job tmdbInitialCollectJob;
    @Mock private Job tmdbDailyCollectJob;

    private DataCollectScheduler scheduler;

    @BeforeEach
    void setUp() {
        // 같은 타입(Job)의 목이 여러 개라 @InjectMocks 주입 순서가 보장되지 않으므로 직접 생성
        scheduler = new DataCollectScheduler(jobLauncher, jobExplorer, sportsDataCollectJob, tmdbInitialCollectJob, tmdbDailyCollectJob);
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
    @DisplayName("락 획득 성공 시 sportsDataCollectJob을 실행한다")
    void runSportsJobWithLock_runsJob_whenLockAcquired() throws Exception {
        // given
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
    @DisplayName("락이 이미 점유 중이면 Job을 실행하지 않고 skip한다")
    void runSportsJobWithLock_skipsJob_whenLockAlreadyHeld() throws Exception {
        // given: ReentrantLock은 같은 스레드가 재진입 가능하므로 별도 스레드에서 락 선점
        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(scheduler, "sportsCollectLock");
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            lock.lock();
            lockAcquired.countDown();
            try {
                testDone.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        lockAcquired.await();

        try {
            // when
            scheduler.runSportsJobWithLock("2025-2026");

            // then: 락 점유 중이라 Job 실행 안 됨
            verify(jobLauncher, never()).run(any(), any());
        } finally {
            testDone.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Job 실행 후 락이 반드시 해제된다")
    void runSportsJobWithLock_releasesLock_afterJobExecution() throws Exception {
        // given
        JobExecution jobExecution = mock(JobExecution.class);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getJobId()).thenReturn(1L);
        when(jobLauncher.run(eq(sportsDataCollectJob), any())).thenReturn(jobExecution);

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(scheduler, "sportsCollectLock");

        // when
        scheduler.runSportsJobWithLock("2025-2026");

        // then: 실행 후 락이 해제되어 있어야 함
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    @DisplayName("Job 실행 중 예외가 발생해도 락이 해제된다")
    void runSportsJobWithLock_releasesLock_evenWhenJobThrows() throws Exception {
        // given
        when(jobLauncher.run(eq(sportsDataCollectJob), any()))
            .thenThrow(new RuntimeException("Job 실패"));

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(scheduler, "sportsCollectLock");

        // when
        assertThatThrownBy(() -> scheduler.runSportsJobWithLock("2025-2026"))
            .isInstanceOf(RuntimeException.class);

        // then: 예외 발생해도 락 해제
        assertThat(lock.isLocked()).isFalse();
    }
}
