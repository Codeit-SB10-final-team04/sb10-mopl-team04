package com.team04.mopl.review.repository;

import static org.assertj.core.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.dto.request.ReviewPageRequest;
import com.team04.mopl.review.dto.response.ReviewCursorPage;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.enums.ReviewSortBy;

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

	// ========== getReviews (QueryDSL) ==========

	@Test
	@DisplayName("해당 콘텐츠의 삭제되지 않은 리뷰만 조회한다")
	void getReviews_returnsOnlyActiveReviews() {
		// given
		insertReview(UUID.randomUUID(), userId, content.getId(), "리뷰1", (short)5, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "리뷰2", (short)4, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "삭제된 리뷰", (short)3, Instant.now());

		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), null, null, 20, SortDirection.DESCENDING, ReviewSortBy.createdAt
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		assertThat(result.reviewList()).hasSize(2);
		assertThat(result.totalCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("createdAt 내림차순 정렬이 정상 동작한다")
	void getReviews_sortsByCreatedAtDescending() {
		// given
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		UUID id3 = UUID.randomUUID();

		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id1, userId, content.getId(), "첫번째", (short)3,
			Timestamp.from(Instant.parse("2026-07-01T10:00:00Z"))
		);
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id2, userId, content.getId(), "두번째", (short)4,
			Timestamp.from(Instant.parse("2026-07-02T10:00:00Z"))
		);
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id3, userId, content.getId(), "세번째", (short)5,
			Timestamp.from(Instant.parse("2026-07-03T10:00:00Z"))
		);

		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), null, null, 20, SortDirection.DESCENDING, ReviewSortBy.createdAt
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		List<Review> reviews = result.reviewList();
		assertThat(reviews).hasSize(3);
		assertThat(reviews.get(0).getText()).isEqualTo("세번째");
		assertThat(reviews.get(1).getText()).isEqualTo("두번째");
		assertThat(reviews.get(2).getText()).isEqualTo("첫번째");
	}

	@Test
	@DisplayName("rating 내림차순 정렬이 정상 동작한다")
	void getReviews_sortsByRatingDescending() {
		// given
		insertReview(UUID.randomUUID(), userId, content.getId(), "평점3", (short)3, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "평점5", (short)5, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "평점1", (short)1, null);

		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), null, null, 20, SortDirection.DESCENDING, ReviewSortBy.rating
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		List<Review> reviews = result.reviewList();
		assertThat(reviews).hasSize(3);
		assertThat(reviews.get(0).getRating()).isEqualTo((short)5);
		assertThat(reviews.get(1).getRating()).isEqualTo((short)3);
		assertThat(reviews.get(2).getRating()).isEqualTo((short)1);
	}

	@Test
	@DisplayName("limit보다 데이터가 많으면 hasNext가 true이다")
	void getReviews_hasNextTrue_whenMoreDataExists() {
		// given
		insertReview(UUID.randomUUID(), userId, content.getId(), "리뷰1", (short)5, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "리뷰2", (short)4, null);
		insertReview(UUID.randomUUID(), userId, content.getId(), "리뷰3", (short)3, null);

		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), null, null, 2, SortDirection.DESCENDING, ReviewSortBy.createdAt
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		assertThat(result.reviewList()).hasSize(2);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.totalCount()).isEqualTo(3L);
	}

	@Test
	@DisplayName("커서 조건이 있으면 해당 위치 이후 데이터만 조회한다")
	void getReviews_returnDataAfterCursor() {
		// given
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		UUID id3 = UUID.randomUUID();

		Instant time1 = Instant.parse("2026-07-01T10:00:00Z");
		Instant time2 = Instant.parse("2026-07-02T10:00:00Z");
		Instant time3 = Instant.parse("2026-07-03T10:00:00Z");

		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id1, userId, content.getId(), "첫번째", (short)3, Timestamp.from(time1)
		);
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id2, userId, content.getId(), "두번째", (short)4, Timestamp.from(time2)
		);
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
				""",
			id3, userId, content.getId(), "세번째", (short)5, Timestamp.from(time3)
		);

		// 커서: time3(세번째) 이후 → 두번째, 첫번째만 조회 (DESCENDING)
		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), time3.toString(), id3, 20, SortDirection.DESCENDING, ReviewSortBy.createdAt
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		List<Review> reviews = result.reviewList();
		assertThat(reviews).hasSize(2);
		assertThat(reviews.get(0).getText()).isEqualTo("두번째");
		assertThat(reviews.get(1).getText()).isEqualTo("첫번째");
	}

	@Test
	@DisplayName("조회 결과가 없으면 빈 리스트를 반환한다")
	void getReviews_returnEmptyList_whenNoReviews() {
		// given
		ReviewPageRequest request = new ReviewPageRequest(
			content.getId(), null, null, 20, SortDirection.DESCENDING, ReviewSortBy.createdAt
		);

		// when
		ReviewCursorPage result = reviewRepository.getReviews(request);

		// then
		assertThat(result.reviewList()).isEmpty();
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(0L);
	}

	private void insertReview(UUID id, UUID userId, UUID contentId, String text, short rating, Instant deletedAt) {
		jdbcTemplate.update("""
				INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			id, userId, contentId, text, rating,
			deletedAt != null ? Timestamp.from(deletedAt) : null
		);
	}
}
