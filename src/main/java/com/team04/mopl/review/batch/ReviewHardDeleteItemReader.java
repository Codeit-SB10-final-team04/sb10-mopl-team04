package com.team04.mopl.review.batch;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ReviewHardDeleteItemReader {

	private final DataSource dataSource;

	@Value("${review.hard-delete.retention-days}")
	private long retentionDays;

	@Bean
	@StepScope
	public JdbcCursorItemReader<UUID> reviewHardDeleteReader() {
		Instant deletedAtBefore = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

		log.info("[REVIEW_HARD_DELETE] Reader 시작: retentionDays={}, cutoff={}", retentionDays, deletedAtBefore);

		return new JdbcCursorItemReaderBuilder<UUID>()
			.name("reviewHardDeleteReader")
			.dataSource(dataSource)
			.sql("""
					SELECT id
					FROM content_reviews
					WHERE deleted_at IS NOT NULL
					AND deleted_at < ?
					ORDER BY deleted_at ASC, id ASC
				""")
			.preparedStatementSetter(ps -> ps.setTimestamp(1, Timestamp.from(deletedAtBefore)))
			.rowMapper((rs, rowNum) -> rs.getObject("id", UUID.class))
			.fetchSize(100)
			.build();
	}
}
