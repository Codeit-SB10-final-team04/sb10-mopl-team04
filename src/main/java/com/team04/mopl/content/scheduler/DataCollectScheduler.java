package com.team04.mopl.content.scheduler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.processing.Generated;

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

import com.team04.mopl.common.redis.DistributedLock;

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
 *
 * <h3>동시 실행 방지</h3>
 * <ul>
 *   <li>{@code sportsCollectLock}: 초기 수집과 주기 수집이 동시에 실행되지 않도록 프로세스 레벨에서 차단</li>
 *   <li>{@link #isSeasonSkippable}: COMPLETED + RUNNING 상태 모두 확인해 시즌 단위 중복 실행 방지</li>
 * </ul>
 */

@Generated("jacoco-exclude") // jacoco 테스트 커버리지 제외
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectScheduler {

	private static final List<String> INITIAL_SEASONS = List.of(
		"2020-2021",
		"2021-2022",
		"2022-2023",
		"2023-2024",
		"2024-2025",
		"2025-2026"
	);

	private static final String CURRENT_SEASON = "2025-2026";

	private final DistributedLock distributedLock;
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
			distributedLock.executeWithLock("batch:tmdb-initial", 0, 3600, () -> {
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
		});
	}

	/**
	 * 앱 기동 시 SportsDB 초기 수집 (시즌별 최초 1회)
	 *
	 * <p>COMPLETED 또는 RUNNING 상태인 시즌은 skip.
	 * 락 획득 실패 시 주기 수집이 실행 중인 것으로 판단하고 전체 초기 수집을 포기한다.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void runSportsInitialCollectIfNeeded() {
		CompletableFuture.runAsync(() -> {
			log.info("[Scheduler] SportsDB 초기 수집 확인 시작");

			for (String season : INITIAL_SEASONS) {
				if (isSeasonSkippable(season)) {
					log.info("[Scheduler] SportsDB 시즌 {} 이미 실행 중이거나 완료 → skip", season);
					continue;
				}

				log.info("[Scheduler] SportsDB 시즌 {} 수집 시작", season);
				try {
					runSportsJobWithLock(season);
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
	@Scheduled(cron = "0 0 3 * * MON", zone = "Asia/Seoul")
	public void runSportsWeeklyCollectJob() {
		log.info("[Scheduler] SportsDB 주기 수집 시작 (시즌: {})", CURRENT_SEASON);
		try {
			runSportsJobWithLock(CURRENT_SEASON);
			log.info("[Scheduler] SportsDB 주기 수집 완료");
		} catch (Exception e) {
			log.error("[Scheduler] SportsDB 주기 수집 실패: {}", e.getMessage(), e);
		}
	}

	// 매일 새벽 4시 TMDB 주기 수집
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void runTmdbDailyCollectJob() {
		log.info("[Scheduler] TMDB 주기 수집 시작");
		boolean executed = distributedLock.executeWithLock("batch:tmdb-daily", 0, 1800, () -> {
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
		});

		if (!executed) {
			log.warn("[Scheduler] TMDB 주기 수집 - 다른 서버에서 실행 중, skip");
		}
	}

	// 분산 락을 획득한 뒤 sportsDataCollectJob 실행, 다른 서버에서 실행 중이면 skip
	void runSportsJobWithLock(String season) throws Exception {
		boolean executed = distributedLock.executeWithLock(
			"batch:sports:" + season, 0, 3600, () -> {
				try {
					JobParameters params = new JobParametersBuilder()
						.addString("season", season)
						.addLong("timestamp", System.currentTimeMillis())
						.toJobParameters();

					JobExecution jobExecution = jobLauncher.run(sportsDataCollectJob, params);
					log.info("[Scheduler] sportsDataCollectJob 완료 - season: {}, status: {}, jobId: {}",
						season, jobExecution.getStatus(), jobExecution.getJobId());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

		if (!executed) {
			log.warn("[Scheduler] SportsDB 다른 서버에서 수집 중, skip: season={}", season);
		}
	}

	/**
	 * 해당 시즌이 이미 실행 중이거나 완료된 경우 true를 반환한다.
	 * COMPLETED / STARTED / STARTING 상태를 모두 확인해 중복 실행을 방지한다.
	 */
	boolean isSeasonSkippable(String season) {
		return jobExplorer.getJobInstances("sportsDataCollectJob", 0, Integer.MAX_VALUE)
			.stream()
			.flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
			.filter(exec -> season.equals(exec.getJobParameters().getString("season")))
			.anyMatch(exec ->
				exec.getStatus() == BatchStatus.COMPLETED
					|| exec.getStatus() == BatchStatus.STARTED
					|| exec.getStatus() == BatchStatus.STARTING
			);
	}
}
