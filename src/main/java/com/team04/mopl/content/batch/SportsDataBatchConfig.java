package com.team04.mopl.content.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import com.team04.mopl.content.batch.step.LeagueCollectTasklet;
import com.team04.mopl.content.batch.step.MatchCollectTasklet;

import lombok.RequiredArgsConstructor;

/**
 스포츠 데이터 수집 Spring Batch
 Job: sportsDataCollectJob
 Step 1 (leagueCollectStep): 리그 ID 수집 → JobContext에 저장
 Step 2 (eventCollectStep) : 리그별 경기 수집 → contents 테이블에 저장
 */
@Configuration
@EnableRetry
@EnableScheduling
@RequiredArgsConstructor
public class SportsDataBatchConfig {

	private final LeagueCollectTasklet leagueCollectTasklet;
	private final MatchCollectTasklet matchCollectTasklet;

	/**
	 스포츠 데이터 수집 Job
	 leagueCollectStep → eventCollectStep 순서로 실행
	 */
	@Bean
	public Job sportsDataCollectJob(
		JobRepository jobRepository,
		Step leagueCollectStep,
		Step matchCollectStep
	) {
		return new JobBuilder("sportsDataCollectJob", jobRepository)
			.start(leagueCollectStep)
			.next(matchCollectStep)
			.build();
	}

	/**
	 Step 1: 리그 ID 수집
	 /all_leagues.php → 리그 ID 목록을 JobContext에 저장
	 */
	@Bean
	public Step leagueCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("leagueCollectStep", jobRepository)
			.tasklet(leagueCollectTasklet, transactionManager)
			.build();
	}

	/**
	 Step 2: 경기 수집
	 JobContext에서 리그 ID 읽기 → /eventsseason.php + /lookupevent.php → Content 저장
	 */
	@Bean
	public Step matchCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("matchCollectStep", jobRepository)
			.tasklet(matchCollectTasklet, transactionManager)
			.build();
	}
}
