package com.team04.mopl.notification.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import com.team04.mopl.common.exception.BatchException;

@ExtendWith(MockitoExtension.class)
class NotificationHardDeleteBatchRunnerTest {

	@Mock
	private JobLauncher jobLauncher;

	@Mock
	private Job notificationHardDeleteJob;

	@InjectMocks
	private NotificationHardDeleteBatchRunner notificationHardDeleteBatchRunner;

	@Test
	@DisplayName("Runner는 deleteDate와 runId를 JobParameter로 전달해 Job을 실행한다.")
	void run_launchJob() throws Exception {
		// given
		LocalDate deleteDate = LocalDate.of(2026, 6, 24);

		JobExecution jobExecution = mock(JobExecution.class);

		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.COMPLETED);
		when(jobLauncher.run(eq(notificationHardDeleteJob), any(JobParameters.class))).thenReturn(jobExecution)
			.thenReturn(jobExecution);

		// when
		notificationHardDeleteBatchRunner.run(deleteDate);

		// then
		ArgumentCaptor<JobParameters> jobParametersArgumentCaptor = ArgumentCaptor.forClass(JobParameters.class);

		verify(jobLauncher).run(eq(notificationHardDeleteJob), jobParametersArgumentCaptor.capture());

		JobParameters jobParameters = jobParametersArgumentCaptor.getValue();
		assertEquals(deleteDate, jobParameters.getLocalDate("deleteDate"));
		assertNotNull(jobParameters.getLong("runId"));
	}

	@Test
	@DisplayName("Job 배치 실행 결과가 COMPLETE가 아니면 예외를 발생시킨다.")
	void run_throwException_whenJobStatusIsNotComplete() throws Exception {
		// given
		LocalDate deleteDate = LocalDate.of(2026, 6, 24);

		JobExecution jobExecution = mock(JobExecution.class);

		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.FAILED);
		when(jobLauncher.run(eq(notificationHardDeleteJob), any(JobParameters.class))).thenReturn(jobExecution)
			.thenReturn(jobExecution);

		// when
		assertThrows(BatchException.class,
			() -> notificationHardDeleteBatchRunner.run(deleteDate));

		// then
		ArgumentCaptor<JobParameters> jobParametersArgumentCaptor = ArgumentCaptor.forClass(JobParameters.class);

		verify(jobLauncher).run(eq(notificationHardDeleteJob), jobParametersArgumentCaptor.capture());

		JobParameters jobParameters = jobParametersArgumentCaptor.getValue();
		assertEquals(deleteDate, jobParameters.getLocalDate("deleteDate"));
		assertNotNull(jobParameters.getLong("runId"));
	}
}