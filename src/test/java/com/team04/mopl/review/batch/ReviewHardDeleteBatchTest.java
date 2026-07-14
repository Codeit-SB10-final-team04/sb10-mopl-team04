package com.team04.mopl.review.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;
import com.team04.mopl.review.repository.ReviewRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:review-hard-delete-batch-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-batch-test.sql",
	"spring.batch.jdbc.initialize-schema=always",
	"spring.batch.job.enabled=false"
})
public class ReviewHardDeleteBatchTest {

	// ===================================================================
	// [ES Mocking 안내]
	// application-test.yml에서 ES 자동 설정을 제외했기 때문에,
	// 전체 컨텍스트 로드가 필요한 통합 테스트(@SpringBootTest)에서는
	// ES 관련 빈(Bean) 생성 실패(UnsatisfiedDependencyException)가 발생합니다.
	// 이를 방지하기 위해 컨텍스트 로드용 가짜 빈(MockBean)을 주입합니다.
	//
	// TODO: 향후 ES 실제 연동 테스트 필요 시 Testcontainers 환경으로 분리
	// ===================================================================

	@MockitoBean
	private ConversationElasticSearchRepository conversationElasticSearchRepository;

	@MockitoBean
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job reviewHardDeleteJob;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("리뷰 물리 삭제 배치는 retention-days 이상 경과한 논리 삭제 리뷰만 삭제한다.")
	void reviewHardDeleteJob_deleteOnlySoftDeletedReviews() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		insertUser(userId);
		insertContent(contentId);

		UUID deletedReviewId1 = UUID.randomUUID();
		UUID deletedReviewId2 = UUID.randomUUID();
		UUID recentlyDeletedReviewId = UUID.randomUUID();
		UUID activeReviewId = UUID.randomUUID();

		Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
		Instant thirtyMinutesAgo = Instant.now().minus(30, ChronoUnit.MINUTES);

		insertReview(deletedReviewId1, userId, contentId, "삭제된 리뷰1", (short)3, twoDaysAgo);
		insertReview(deletedReviewId2, userId, contentId, "삭제된 리뷰2", (short)4, twoDaysAgo);
		insertReview(recentlyDeletedReviewId, userId, contentId, "최근 삭제된 리뷰", (short)5, thirtyMinutesAgo);
		insertReview(activeReviewId, userId, contentId, "활성 리뷰", (short)5, null);

		JobParameters jobParameters = new JobParametersBuilder()
			.addLong("runId", System.currentTimeMillis())
			.toJobParameters();

		// when
		JobExecution jobExecution = jobLauncher.run(reviewHardDeleteJob, jobParameters);

		// then
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
		assertFalse(reviewRepository.findById(deletedReviewId1).isPresent());
		assertFalse(reviewRepository.findById(deletedReviewId2).isPresent());
		assertTrue(reviewRepository.findById(recentlyDeletedReviewId).isPresent());
		assertTrue(reviewRepository.findById(activeReviewId).isPresent());
	}

	private void insertUser(UUID userId) {
		jdbcTemplate.update("""
				INSERT INTO users (id, name, email, email_type, password_hash, role, is_locked, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			userId, "테스트유저", "test@test.com", "GENERAL", "hashed_password", "USER", false
		);
	}

	private void insertContent(UUID contentId) {
		jdbcTemplate.update("""
				INSERT INTO contents (
					id, external_id, source, title, type, description,
					thumbnail_url, average_rating, review_count, watcher_count,
					deleted_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			contentId, null, "MANUAL", "테스트 콘텐츠", "movie", "테스트 설명",
			"/thumbnails/test.jpg", 0.00, 0, 0
		);
	}

	private void insertReview(UUID reviewId, UUID userId, UUID contentId, String text, short rating,
		Instant deletedAt) {
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			reviewId, userId, contentId, text, rating,
			deletedAt != null ? Timestamp.from(deletedAt) : null
		);
	}
}
