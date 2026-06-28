package com.team04.mopl.content.scheduler;

import java.util.List;
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
 *   <li>TMDB 초기 수집: 앱 최초 기동 시 1회 자동 실행</li>
 *   <li>SportsDB 초기 수집: 앱 최초 기동 시 20-21 ~ 25-26 시즌 순차 수집</li>
 *   <li>SportsDB 주기 수집: 매주 월요일 새벽 3시 (현재 시즌 25-26)</li>
 *   <li>TMDB 주기 수집: 매일 새벽 4시</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectScheduler {

	// 초기 수집 시즌 목록 (2020-21 ~ 2025-26)
	private static final List<String> INITIAL_SEASONS = List.of(
		"2020-2021",
		"2021-2022",
		"2022-2023",
		"2023-2024",
		"2024-2025",
		"2025-2026"
	);

	private static final String CURRENT_SEASON = "2025-2026";

	private final JobLauncher jobLauncher;
	private final JobExplorer jobExplorer;
	private final Job sportsDataCollectJob;
	private final Job tmdbInitialCollectJob;
	private final Job tmdbDailyCollectJob;

	/**
	 * 앱 기동 시 TMDB 초기 수집 (최초 1회)
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void runTmdbInitialCollectIfNeeded() {
		CompletableFuture.runAsync(() -> {
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
				log.info("[Scheduler] TMDB 초기 수집 완료 - status: {}, jobId: {}",
					jobExecution.getStatus(), jobExecution.getJobId());
			} catch (Exception e) {
				log.error("[Scheduler] TMDB 초기 수집 실패: {}", e.getMessage(), e);
			}
		});
	}

	/**
	 * 앱 기동 시 SportsDB 초기 수집 (시즌별 최초 1회)
	 *
	 * <p>INITIAL_SEASONS를 순차적으로 돌며 해당 시즌의 COMPLETED 이력이 없으면 실행.
	 * 이미 성공한 시즌은 skip하므로 재기동 시 중복 수집 없음.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void runSportsInitialCollectIfNeeded() {
		CompletableFuture.runAsync(() -> {
			log.info("[Scheduler] SportsDB 초기 수집 확인 시작");

			for (String season : INITIAL_SEASONS) {
				if (isSeasonAlreadyCompleted(season)) {
					log.info("[Scheduler] SportsDB 시즌 {} 이미 수집 완료 → skip", season);
					continue;
				}

				log.info("[Scheduler] SportsDB 시즌 {} 수집 시작", season);
				try {
					runSportsJob(season);
					log.info("[Scheduler] SportsDB 시즌 {} 수집 완료", season);
				} catch (Exception e) {
					log.error("[Scheduler] SportsDB 시즌 {} 수집 실패: {}", season, e.getMessage(), e);
				}
			}

			log.info("[Scheduler] SportsDB 초기 수집 전체 완료");
		});
	}

	/**
	 * 매주 월요일 새벽 3시 SportsDB 주기 수집 (현재 시즌)
	 */
	@Scheduled(cron = "0 0 3 * * MON")
	public void runSportsWeeklyCollectJob() {
		log.info("[Scheduler] SportsDB 주기 수집 시작 (시즌: {})", CURRENT_SEASON);
		try {
			runSportsJob(CURRENT_SEASON);
			log.info("[Scheduler] SportsDB 주기 수집 완료");
		} catch (Exception e) {
			log.error("[Scheduler] SportsDB 주기 수집 실패: {}", e.getMessage(), e);
		}
	}

	/**
	 * 매일 새벽 4시 TMDB 주기 수집
	 */
	@Scheduled(cron = "0 0 4 * * *")
	public void runTmdbDailyCollectJob() {
		log.info("[Scheduler] TMDB 주기 수집 시작");
		try {
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			JobExecution jobExecution = jobLauncher.run(tmdbDailyCollectJob, params);
			log.info("[Scheduler] TMDB 주기 수집 완료 - status: {}, jobId: {}",
				jobExecution.getStatus(), jobExecution.getJobId());
		} catch (Exception e) {
			log.error("[Scheduler] TMDB 주기 수집 실패: {}", e.getMessage(), e);
		}
	}

	// 해당 시즌의 sportsDataCollectJob이 COMPLETED 이력이 있는지 확인
	private boolean isSeasonAlreadyCompleted(String season) {
		return jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE)
			.stream()
			.flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
			.filter(exec -> exec.getStatus() == BatchStatus.COMPLETED)
			.anyMatch(exec -> season.equals(exec.getJobParameters().getString("season")));
	}

	// season 파라미터를 포함한 sportsDataCollectJob 실행
	private void runSportsJob(String season) throws Exception {
		JobParameters params = new JobParametersBuilder()
			.addString("season", season)
			.addLong("timestamp", System.currentTimeMillis())
			.toJobParameters();

		JobExecution jobExecution = jobLauncher.run(sportsDataCollectJob, params);
		log.info("[Scheduler] sportsDataCollectJob 완료 - season: {}, status: {}, jobId: {}",
			season, jobExecution.getStatus(), jobExecution.getJobId());
	}
}
