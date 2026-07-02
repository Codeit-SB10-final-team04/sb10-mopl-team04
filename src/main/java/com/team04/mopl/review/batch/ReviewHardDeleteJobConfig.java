package com.team04.mopl.review.batch;

import java.util.UUID;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ReviewHardDeleteJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;

	@Bean
	public Job reviewHardDeleteJob(Step reviewHardDeleteStep) {
		return new JobBuilder("reviewHardDeleteJob", jobRepository)
			.start(reviewHardDeleteStep)
			.build();
	}

	@Bean
	public Step reviewHardDeleteStep(
		ItemReader<UUID> reviewHardDeleteReader,
		ItemWriter<UUID> reviewHardDeleteItemWriter
	) {
		return new StepBuilder("reviewHardDeleteStep", jobRepository)
			.<UUID, UUID>chunk(100, transactionManager)
			.reader(reviewHardDeleteReader)
			.writer(reviewHardDeleteItemWriter)
			.build();
	}
}
