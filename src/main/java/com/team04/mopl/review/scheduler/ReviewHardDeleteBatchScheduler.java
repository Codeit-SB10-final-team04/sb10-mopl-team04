package com.team04.mopl.review.scheduler;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.batch.BatchTimeZone;
import com.team04.mopl.common.exception.BatchException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewHardDeleteBatchScheduler {

	private final JobLauncher jobLauncher;
	private final Job reviewHardDeleteJob;

	@Scheduled(
		cron = "${review.hard-delete.cron}",
		zone = BatchTimeZone.ZONE_ID
	)
	public void runReviewHardDeleteBatch() {
		try {
			JobParameters jobParameters = new JobParametersBuilder()
				.addLong("runId", System.currentTimeMillis())
				.toJobParameters();

			JobExecution jobExecution = jobLauncher.run(reviewHardDeleteJob, jobParameters);

			if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
				throw new IllegalStateException("리뷰 물리 삭제 배치 실패. jobExecutionId="
					+ jobExecution.getId() + ", status=" + jobExecution.getStatus());
			}
		} catch (Exception e) {
			throw new BatchException("리뷰 물리 삭제 배치에 실패했습니다. ", e);
		}
	}
}
