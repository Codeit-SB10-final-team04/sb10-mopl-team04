package com.team04.mopl.content.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 스포츠 데이터 수집 배치 스케줄러

 매일 새벽 3시에 sportsDataCollectJob을 자동 실행

 JobParameters에 timestamp를 추가하는 이유:
 Spring Batch는 동일한 JobParameters로 완료된 Job을 재실행하지 않음
 매 실행마다 timestamp를 추가해 각 실행을 별도 인스턴스로 처리

 자동 실행 방지:
 application.yml의 spring.batch.job.enabled=false 설정으로
 애플리케이션 시작 시 자동 실행을 막고 스케줄러에서만 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectScheduler {

	private final JobLauncher jobLauncher;
	private final Job sportsDataCollectJob;

	/**
	 매일 새벽 3시 스포츠 데이터 수집 Job 실행
	 실패 시 예외를 catch하여 로그만 기록, 다음 스케줄 실행에 영향 없음
	 */
	@Scheduled(cron = "0 0 3 * * *")
	public void runSportsDataCollectJob() {
		log.info("[Scheduler] 스포츠 데이터 수집 Job 실행 시작");
		try {
			// 매 실행마다 고유한 파라미터 부여 → 동일 Job 재실행 허용
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			jobLauncher.run(sportsDataCollectJob, params);
			log.info("[Scheduler] 스포츠 데이터 수집 Job 실행 완료");
		} catch (Exception e) {
			log.error("[Scheduler] 스포츠 데이터 수집 Job 실행 실패: {}", e.getMessage(), e);
		}
	}
}
