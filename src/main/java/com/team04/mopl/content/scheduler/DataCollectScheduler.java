package com.team04.mopl.content.scheduler;

import java.util.concurrent.CompletableFuture;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 데이터 수집 배치 스케줄러
 *
 * <h3>실행 목록</h3>
 * <ul>
 *   <li>TMDB 초기 수집: 애플리케이션 최초 기동 시 1회 자동 실행 ({@code tmdbInitialCollectJob})</li>
 *   <li>스포츠 데이터: 매일 새벽 3시 ({@code sportsDataCollectJob})</li>
 *   <li>TMDB 주기 수집: 매일 새벽 4시 ({@code tmdbDailyCollectJob})</li>
 * </ul>
 *
 * <p>자동 실행 방지: {@code spring.batch.job.enabled=false} 설정으로
 * 애플리케이션 시작 시 배치 자동 실행을 막고 스케줄러에서만 실행.
 *
 * <p>매 실행마다 {@code timestamp}를 JobParameter로 추가하는 이유:
 * Spring Batch는 동일한 JobParameters로 완료된 Job을 재실행하지 않으므로
 * 매 실행을 별도 인스턴스로 처리하기 위함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectScheduler {

	private final JobLauncher jobLauncher;
	private final JobExplorer jobExplorer;
	private final Job sportsDataCollectJob;
	private final Job tmdbInitialCollectJob;
	private final Job tmdbDailyCollectJob;

	/**
	 * 애플리케이션 최초 기동 시 TMDB 초기 수집 Job 실행
	 *
	 * <p>{@link ApplicationReadyEvent}: 애플리케이션이 완전히 기동된 후 발생.
	 *
	 * <p>기동 스레드 블로킹 방지: {@link CompletableFuture#runAsync}로 별도 스레드에서 실행.
	 * 초기 수집은 수만 건 처리로 오래 걸릴 수 있어 메인 스레드와 분리.
	 *
	 * <p>{@link JobExplorer}로 전체 실행 이력 중 COMPLETED 여부 확인:
	 * <ul>
	 *   <li>이력 있음 → skip (한 번이라도 성공했으면 영구 skip)</li>
	 *   <li>이력 없음 → 최초 실행으로 판단, 전체 수집 실행</li>
	 * </ul>
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void runTmdbInitialCollectIfNeeded() {
		CompletableFuture.runAsync(() -> {
			// 전체 이력 조회: timestamp로 매 실행마다 새 인스턴스가 생기므로
			// 최신 1개만 보면 과거 성공 이력을 놓칠 수 있음 → Integer.MAX_VALUE로 전체 확인
			boolean hasCompleted = jobExplorer.getJobInstances("tmdbInitialCollectJob", 0, Integer.MAX_VALUE)
				.stream()
				.flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
				.anyMatch(execution -> execution.getStatus() == BatchStatus.COMPLETED);

			if (hasCompleted) {
				log.info("[Scheduler] TMDB 초기 수집 이미 완료된 이력 있음 → skip");
				return;
			}

			log.info("[Scheduler] TMDB 초기 수집 시작 (최초 실행)");
			try {
				JobParameters params = new JobParametersBuilder()
					.addLong("timestamp", System.currentTimeMillis())
					.toJobParameters();

				JobExecution jobExecution = jobLauncher.run(tmdbInitialCollectJob, params);
				log.info("[Scheduler] TMDB 초기 수집 완료 - status: {}, exitCode: {}, jobId: {}",
					jobExecution.getStatus(),
					jobExecution.getExitStatus().getExitCode(),
					jobExecution.getJobId());
			} catch (Exception e) {
				log.error("[Scheduler] TMDB 초기 수집 실패: {}", e.getMessage(), e);
			}
		});
	}

	/**
	 * 매일 새벽 3시 스포츠 데이터 수집 Job 실행
	 *
	 * <p>실패 시 예외를 catch하여 로그만 기록, 다음 스케줄 실행에 영향 없음.
	 */
	@Scheduled(cron = "0 0 3 * * *")
	public void runSportsDataCollectJob() {
		log.info("[Scheduler] 스포츠 데이터 수집 Job 실행 시작");
		try {
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			JobExecution jobExecution = jobLauncher.run(sportsDataCollectJob, params);
			log.info("[Scheduler] 스포츠 데이터 수집 Job 실행 완료 - status: {}, exitCode: {}, jobId: {}",
				jobExecution.getStatus(),
				jobExecution.getExitStatus().getExitCode(),
				jobExecution.getJobId());
		} catch (Exception e) {
			log.error("[Scheduler] 스포츠 데이터 수집 Job 실행 실패: {}", e.getMessage(), e);
		}
	}

	/**
	 * 매일 새벽 4시 TMDB 주기 수집 Job 실행
	 *
	 * <p>수집 대상: {@code movie/upcoming}, {@code tv/on_the_air}
	 *
	 * <p>실패 시 예외를 catch하여 로그만 기록, 다음 스케줄 실행에 영향 없음.
	 */
	@Scheduled(cron = "0 0 4 * * *")
	public void runTmdbDailyCollectJob() {
		log.info("[Scheduler] TMDB 주기 수집 Job 실행 시작");
		try {
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			JobExecution jobExecution = jobLauncher.run(tmdbDailyCollectJob, params);
			log.info("[Scheduler] TMDB 주기 수집 Job 실행 완료 - status: {}, exitCode: {}, jobId: {}",
				jobExecution.getStatus(),
				jobExecution.getExitStatus().getExitCode(),
				jobExecution.getJobId());
		} catch (Exception e) {
			log.error("[Scheduler] TMDB 주기 수집 Job 실행 실패: {}", e.getMessage(), e);
		}
	}
}
