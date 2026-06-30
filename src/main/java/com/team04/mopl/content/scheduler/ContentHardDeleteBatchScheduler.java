package com.team04.mopl.content.scheduler;

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

// 정해진 시간에 논리 삭제된 콘텐츠를 물리 삭제하는 스케줄러
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentHardDeleteBatchScheduler {

	private final JobLauncher jobLauncher;
	private final Job contentHardDeleteJob;

	@Scheduled(
		cron = "${content.hard-delete.cron}",
		zone = BatchTimeZone.ZONE_ID
	)
	public void runContentHardDeleteBatch() {
		log.info("[CONTENT_HARD_DELETE_BATCH] 콘텐츠 물리 삭제 스케줄 시작");

		try {
			JobParameters jobParameters = new JobParametersBuilder()
				.addLong("runId", System.currentTimeMillis())
				.toJobParameters();

			JobExecution jobExecution = jobLauncher.run(contentHardDeleteJob, jobParameters);

			if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
				throw new IllegalStateException("콘텐츠 물리 삭제 배치 실패. jobExecutionId="
					+ jobExecution.getId() + ", status=" + jobExecution.getStatus());
			}

			log.info("[CONTENT_HARD_DELETE_BATCH] 콘텐츠 물리 삭제 스케줄 완료");
		} catch (Exception e) {
			log.error("[CONTENT_HARD_DELETE_BATCH] 콘텐츠 물리 삭제 배치 실패", e);
			throw new BatchException("콘텐츠 물리 삭제 배치에 실패했습니다.", e);
		}
	}
}
