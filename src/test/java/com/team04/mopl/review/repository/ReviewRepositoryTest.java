package com.team04.mopl.review.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import java.util.Optional;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.entity.Review;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
class ReviewRepositoryTest {

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID userId;
	private Content content;

	@BeforeEach
	void setUp() {
		// H2가 email_type NAMED_ENUM을 인식 못해 users 테이블 생성 실패 → FK 제약도 없으므로 user_id는 임의 UUID 사용
		userId = UUID.randomUUID();

		content = contentRepository.save(Content.builder()
			.title("인터스텔라")
			.type(ContentType.movie)
			.description("우주 탐험 이야기")
			.thumbnailUrl("/thumbnails/test.jpg")
			.build());

		em.flush();
	}

	// ========== existsByUserIdAndContentIdAndDeletedAtIsNull ==========

	@Test
	@DisplayName("삭제되지 않은 리뷰가 존재하면 true를 반환한다")
	void existsByUserIdAndContentIdAndDeletedAtIsNull_returnsTrue_whenReviewExists() {
		// given
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			UUID.randomUUID(), userId, content.getId(), "재밌어요", (short)5
		);

		// when
		boolean result = reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(
			userId, content.getId());

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("리뷰가 소프트 딜리트된 경우 false를 반환한다")
	void existsByUserIdAndContentIdAndDeletedAtIsNull_returnsFalse_whenReviewSoftDeleted() {
		// given
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			UUID.randomUUID(), userId, content.getId(), "재밌어요", (short)5,
			java.sql.Timestamp.from(Instant.now())
		);

		// when
		boolean result = reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(
			userId, content.getId());

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("리뷰가 존재하지 않으면 false를 반환한다")
	void existsByUserIdAndContentIdAndDeletedAtIsNull_returnsFalse_whenReviewNotExists() {
		// when
		boolean result = reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(
			UUID.randomUUID(), UUID.randomUUID());

		// then
		assertThat(result).isFalse();
	}

	// ========== findByIdAndDeletedAtIsNull ==========

	@Test
	@DisplayName("삭제되지 않은 리뷰가 존재하면 Optional로 반환한다")
	void findByIdAndDeletedAtIsNull_returnsReview_whenReviewExists() {
		// given
		UUID reviewId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			reviewId, userId, content.getId(), "재밌어요", (short)5
		);

		// when
		Optional<Review> result = reviewRepository.findByIdAndDeletedAtIsNull(reviewId);

		// then
		assertThat(result).isPresent();
		assertThat(result.get().getText()).isEqualTo("재밌어요");
	}

	@Test
	@DisplayName("소프트 딜리트된 리뷰이면 Optional.empty를 반환한다")
	void findByIdAndDeletedAtIsNull_returnsEmpty_whenReviewSoftDeleted() {
		// given
		UUID reviewId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			reviewId, userId, content.getId(), "재밌어요", (short)5,
			java.sql.Timestamp.from(Instant.now())
		);

		// when
		Optional<Review> result = reviewRepository.findByIdAndDeletedAtIsNull(reviewId);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("존재하지 않는 리뷰 ID이면 Optional.empty를 반환한다")
	void findByIdAndDeletedAtIsNull_returnsEmpty_whenReviewNotExists() {
		// when
		Optional<Review> result = reviewRepository.findByIdAndDeletedAtIsNull(UUID.randomUUID());

		// then
		assertThat(result).isEmpty();
	}
}
