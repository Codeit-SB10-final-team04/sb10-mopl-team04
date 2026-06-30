package com.team04.mopl.content.batch.step;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ContentHardDeleteItemReader {

	private final DataSource dataSource;

	@Bean
	public JdbcCursorItemReader<UUID> contentHardDeleteReader() {
		return new JdbcCursorItemReaderBuilder<UUID>()
			.name("contentHardDeleteReader")
			.dataSource(dataSource)
			.sql("""
					SELECT id
					FROM contents
					WHERE deleted_at IS NOT NULL
					ORDER BY deleted_at ASC, id ASC
				""")
			.rowMapper((rs, rowNum) -> rs.getObject("id", UUID.class))
			.fetchSize(100)
			.build();
	}
}
