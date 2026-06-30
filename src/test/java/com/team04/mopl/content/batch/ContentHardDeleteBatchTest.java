package com.team04.mopl.content.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.team04.mopl.content.repository.ContentRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:content-hard-delete-batch-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-batch-test.sql",
	"spring.batch.jdbc.initialize-schema=always",
	"spring.batch.job.enabled=false"
})
public class ContentHardDeleteBatchTest {

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job contentHardDeleteJob;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("콘텐츠 물리 삭제 배치는 논리 삭제된 콘텐츠만 삭제한다.")
	void contentHardDeleteJob_deleteOnlySoftDeletedContents() throws Exception {
		// given
		UUID deletedContentId1 = UUID.randomUUID();
		UUID deletedContentId2 = UUID.randomUUID();
		UUID activeContentId = UUID.randomUUID();

		insertContent(deletedContentId1, "삭제된 콘텐츠1", Instant.parse("2026-05-01T00:00:00Z"));
		insertContent(deletedContentId2, "삭제된 콘텐츠2", Instant.parse("2026-06-01T00:00:00Z"));
		insertContent(activeContentId, "활성 콘텐츠", null);

		JobParameters jobParameters = new JobParametersBuilder()
			.addLong("runId", System.currentTimeMillis())
			.toJobParameters();

		// when
		JobExecution jobExecution = jobLauncher.run(contentHardDeleteJob, jobParameters);

		// then
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
		assertFalse(contentRepository.findById(deletedContentId1).isPresent());
		assertFalse(contentRepository.findById(deletedContentId2).isPresent());
		assertTrue(contentRepository.findById(activeContentId).isPresent());
	}

	// h2가 sql의 enum 타입을 인식 못하는 문제로 JdbcTemplate을 사용해 직접 insert
	private void insertContent(UUID contentId, String title, Instant deletedAt) {
		jdbcTemplate.update("""
				INSERT INTO contents (
					id, external_id, source, title, type, description,
					thumbnail_url, average_rating, review_count, watcher_count,
					deleted_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			contentId,
			null,
			"MANUAL",
			title,
			"movie",
			"테스트 설명",
			"/thumbnails/test.jpg",
			0.00,
			0,
			0,
			deletedAt != null ? Timestamp.from(deletedAt) : null
		);
	}
}
