package com.team04.mopl.content.batch.delete;

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
public class ContentHardDeleteJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;

	@Bean
	public Job contentHardDeleteJob(Step contentHardDeleteStep) {
		return new JobBuilder("contentHardDeleteJob", jobRepository)
			.start(contentHardDeleteStep)
			.build();
	}

	@Bean
	public Step contentHardDeleteStep(
		ItemReader<UUID> contentHardDeleteReader,
		ItemWriter<UUID> contentHardDeleteItemWriter
	) {
		return new StepBuilder("contentHardDeleteStep", jobRepository)
			.<UUID, UUID>chunk(100, transactionManager)
			.reader(contentHardDeleteReader)
			.writer(contentHardDeleteItemWriter)
			.build();
	}
}
