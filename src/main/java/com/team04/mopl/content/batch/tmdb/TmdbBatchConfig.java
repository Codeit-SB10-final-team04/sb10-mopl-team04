package com.team04.mopl.content.batch.tmdb;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.RequiredArgsConstructor;

/**
 TMDB 데이터 수집 Spring Batch 설정

 Job 1: tmdbInitialCollectJob  — 최초 1회 수동 실행 (스케줄러 별도 트리거)
 Step: tmdbInitialCollectStep → movie/now_playing + tv/on_the_air 전체 수집

 Job 2: tmdbDailyCollectJob   — 매일 스케줄러 자동 실행
 Step: tmdbDailyCollectStep → movie/upcoming + tv/on_the_air 조기 종료 수집
 */
@Configuration
@RequiredArgsConstructor
public class TmdbBatchConfig {

	private final TmdbInitialCollectTasklet tmdbInitialCollectTasklet;
	private final TmdbDailyCollectTasklet tmdbDailyCollectTasklet;

	/**
	 초기 수집 Job (최초 1회)
	 movie/now_playing + tv/on_the_air 전체 페이지 수집
	 */
	@Bean
	public Job tmdbInitialCollectJob(
		JobRepository jobRepository,
		Step tmdbInitialCollectStep
	) {
		return new JobBuilder("tmdbInitialCollectJob", jobRepository)
			.start(tmdbInitialCollectStep)
			.build();
	}

	/**
	 주기 수집 Job (매일 스케줄러)
	 movie/upcoming + tv/on_the_air
	 */
	@Bean
	public Job tmdbDailyCollectJob(
		JobRepository jobRepository,
		Step tmdbDailyCollectStep
	) {
		return new JobBuilder("tmdbDailyCollectJob", jobRepository)
			.start(tmdbDailyCollectStep)
			.build();
	}

	// 초기 수집 용도
	@Bean
	public Step tmdbInitialCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("tmdbInitialCollectStep", jobRepository)
			.tasklet(tmdbInitialCollectTasklet, transactionManager)
			.build();
	}

	// 주기 수집 용도
	@Bean
	public Step tmdbDailyCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("tmdbDailyCollectStep", jobRepository)
			.tasklet(tmdbDailyCollectTasklet, transactionManager)
			.build();
	}
}
