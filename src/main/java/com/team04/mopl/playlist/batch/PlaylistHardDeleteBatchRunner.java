package com.team04.mopl.playlist.batch;

import java.time.LocalDate;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.exception.BatchException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// JobLauncher로 배치 Job 실행
@Component
@Slf4j
@RequiredArgsConstructor
public class PlaylistHardDeleteBatchRunner {

	// Spring Batch Job을 실행하는 객체
	private final JobLauncher jobLauncher;

	// PlaylistHardDeleteJobConfig에서 `@Bean`으로 등록한 Job
	private final Job playlistHardDeleteJob;

	public void run(LocalDate deleteDate) {
		try {
			JobParameters jobParameters = new JobParametersBuilder()
				// Job 내부에서 사용할 삭제 대상 날짜를 파라미터로 추가 (Reader에서 deleteDate 사용 가능)
				.addLocalDate("deleteDate", deleteDate)
				// Spring Batch는 Job 이름 + JobParameters로 JobInstance 실행 이력을 관리
				// 같은 JobParameters로 이미 COMPLETED된 Job은 다시 실행할 수 없다.
				// 스케줄 실행마다 새로운 JobInstance로 실행되도록 runId를 추가
				.addLong("runId", System.currentTimeMillis())
				// Builder에서 만든 값을 JobParameter에 추가
				.toJobParameters();

			log.info("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 물리 삭제 배치 실행: deleteDate={}", deleteDate);

			JobExecution jobExecution = jobLauncher.run(playlistHardDeleteJob, jobParameters);

			if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
				throw new IllegalStateException("플레이리스트 물리 삭제 배치 실패. jobExecutionId="
					+ jobExecution.getId() + ", status=" + jobExecution.getStatus());
			}

			log.info("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 물리 삭제 배치 성공: deleteDate={}", deleteDate);
		} catch (Exception e) {
			log.error("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 물리 삭제 배치 실패: deleteDate={}",
				deleteDate, e);
			throw new BatchException("플레이리스트 물리 삭제 배치에 실패했습니다.", e);
		}
	}
}
