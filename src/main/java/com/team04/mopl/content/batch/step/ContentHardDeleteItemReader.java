package com.team04.mopl.content.batch.step;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ContentHardDeleteItemReader {

	private final DataSource dataSource;

	@Value("${content.hard-delete.retention-days}")
	private long retentionDays;

	@Bean
	public JdbcCursorItemReader<UUID> contentHardDeleteReader() {
		Instant deletedAtBefore = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

		return new JdbcCursorItemReaderBuilder<UUID>()
			.name("contentHardDeleteReader")
			.dataSource(dataSource)
			.sql("""
					SELECT id
					FROM contents
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
