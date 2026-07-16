package com.team04.mopl.content.batch.sports;

import java.util.List;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 스포츠 데이터 수집 Spring Batch
 *
 * Step 1 (leagueCollectStep)     : 리그 ID 수집 → JobContext["leagueIds"]
 * Step 2 (matchListCollectStep)  : 경기 ID 목록 수집 → JobContext["eventIds"]
 * Step 3 (eventDetailCollectStep): 경기 상세 조회 → DB 저장 (chunk 기반)
 */
@Configuration
@EnableRetry
@EnableScheduling
@RequiredArgsConstructor
public class SportsDataBatchConfig {

	private static final int CHUNK_SIZE = 10;

	private final LeagueCollectTasklet leagueCollectTasklet;
	private final MatchListCollectTasklet matchListCollectTasklet;
	private final EventDetailItemProcessor eventDetailItemProcessor;
	private final EventDetailItemWriter eventDetailItemWriter;

	@Bean
	public Job sportsDataCollectJob(
		JobRepository jobRepository,
		Step leagueCollectStep,
		Step matchListCollectStep,
		Step eventDetailCollectStep
	) {
		return new JobBuilder("sportsDataCollectJob", jobRepository)
			.start(leagueCollectStep)
			.next(matchListCollectStep)
			.next(eventDetailCollectStep)
			.build();
	}

	/** Step 1: 리그 ID 수집 */
	@Bean
	public Step leagueCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("leagueCollectStep", jobRepository)
			.tasklet(leagueCollectTasklet, transactionManager)
			.build();
	}

	/** Step 2: 경기 ID 목록 수집 */
	@Bean
	public Step matchListCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager
	) {
		return new StepBuilder("matchListCollectStep", jobRepository)
			.tasklet(matchListCollectTasklet, transactionManager)
			.build();
	}

	/**
	 * Step 3 Reader: JobContext의 eventIds를 StepScope로 주입받아 하나씩 반환
	 * @StepScope Bean이므로 Step 실행 시점에 jobExecutionContext가 확정된 뒤 생성됨 -> eventIds를 Parameter로 받아야함
	 */
	@Bean
	@StepScope
	public EventDetailItemReader eventDetailItemReader(
		@Value("#{jobExecutionContext['eventIds']}") List<String> eventIds
	) {
		return new EventDetailItemReader(eventIds);
	}

	/**
	 * Step 3: 경기 상세 조회 → chunk 단위 DB 저장
	 * faultTolerant + skip 으로 개별 경기 API 조회 실패(EventDetailNotFoundException)만 넘어간다.
	 * DB 오류 등 예상치 못한 예외는 step 실패로 처리된다.
	 */
	@Bean
	public Step eventDetailCollectStep(
		JobRepository jobRepository,
		PlatformTransactionManager transactionManager,
		EventDetailItemReader eventDetailItemReader
	) {
		return new StepBuilder("eventDetailCollectStep", jobRepository)
			.<String, JsonNode>chunk(CHUNK_SIZE, transactionManager)
			.reader(eventDetailItemReader)
			.processor(eventDetailItemProcessor)
			.writer(eventDetailItemWriter)
			.faultTolerant()
			.skip(EventDetailNotFoundException.class)
			.skipLimit(50)
			.build();
	}
}
