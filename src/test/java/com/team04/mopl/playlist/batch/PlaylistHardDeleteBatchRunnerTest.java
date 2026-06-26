package com.team04.mopl.playlist.batch;

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

@ExtendWith(MockitoExtension.class)
class PlaylistHardDeleteBatchRunnerTest {

	@Mock
	private JobLauncher jobLauncher;

	@Mock
	private Job playlistHardDeleteJob;

	@InjectMocks
	private PlaylistHardDeleteBatchRunner playlistHardDeleteBatchRunner;

	@Test
	@DisplayName("Runner는 deleteDate와 runId를 JobParameter로 전달해 Job을 실행한다.")
	void run_launchJob() throws Exception {
		// given
		LocalDate deleteDate = LocalDate.of(2026, 6, 24);

		JobExecution jobExecution = mock(JobExecution.class);

		when(jobExecution.getStatus())
			.thenReturn(BatchStatus.COMPLETED);
		when(jobLauncher.run(eq(playlistHardDeleteJob), any(JobParameters.class)))
			.thenReturn(jobExecution);

		// when
		playlistHardDeleteBatchRunner.run(deleteDate);

		// then
		ArgumentCaptor<JobParameters> jobParametersArgumentCaptor = ArgumentCaptor.forClass(JobParameters.class);

		verify(jobLauncher).run(eq(playlistHardDeleteJob), jobParametersArgumentCaptor.capture());

		JobParameters jobParameters = jobParametersArgumentCaptor.getValue();
		assertEquals(deleteDate, jobParameters.getLocalDate("deleteDate"));
		assertNotNull(jobParameters.getLong("runId"));
	}
}