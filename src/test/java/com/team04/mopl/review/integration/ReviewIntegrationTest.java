package com.team04.mopl.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.exception.ReviewErrorCode;
import com.team04.mopl.review.exception.ReviewException;
import com.team04.mopl.review.repository.ReviewRepository;
import com.team04.mopl.review.service.ReviewService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
class ReviewIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private User user;
	private Content content;

	@BeforeEach
	void setUp() {
		user = createUser("테스트유저", "user-" + UUID.randomUUID() + "@test.com");
		content = contentRepository.save(Content.builder()
			.title("테스트콘텐츠").type(ContentType.movie)
			.description("설명").thumbnailUrl("url").build());
	}

	@Test
	@DisplayName("리뷰를 생성하면 DB에 저장된다")
	void createReview_savesToDb() {
		ReviewCreateRequest request = new ReviewCreateRequest(content.getId(), "재밌어요", (short) 5);

		ReviewDto result = reviewService.createReview(request, userDetails(user));

		assertThat(result.id()).isNotNull();
		assertThat(result.text()).isEqualTo("재밌어요");
		assertThat(result.rating()).isEqualTo((short) 5);
		assertThat(result.contentId()).isEqualTo(content.getId());

		Review saved = reviewRepository.findById(result.id()).orElseThrow();
		assertThat(saved.getText()).isEqualTo("재밌어요");
	}

	@Test
	@DisplayName("같은 유저가 같은 콘텐츠에 두 번째 리뷰를 작성하면 예외가 발생한다")
	void createReview_throwsException_whenDuplicate() {
		ReviewCreateRequest request = new ReviewCreateRequest(content.getId(), "첫 리뷰", (short) 4);
		reviewService.createReview(request, userDetails(user));

		ReviewCreateRequest duplicate = new ReviewCreateRequest(content.getId(), "두 번째 리뷰", (short) 3);

		assertThatThrownBy(() -> reviewService.createReview(duplicate, userDetails(user)))
			.isInstanceOf(ReviewException.class)
			.satisfies(ex -> assertThat(((ReviewException) ex).getErrorCode())
				.isEqualTo(ReviewErrorCode.REVIEW_ALREADY_EXISTS));
	}

	@Test
	@DisplayName("리뷰 오너가 수정하면 text와 rating이 변경된다")
	void updateReview_updatesFields_whenOwner() {
		ReviewCreateRequest createReq = new ReviewCreateRequest(content.getId(), "원래 텍스트", (short) 3);
		ReviewDto created = reviewService.createReview(createReq, userDetails(user));

		ReviewUpdateRequest updateReq = new ReviewUpdateRequest("수정된 텍스트", (short) 5);
		ReviewDto updated = reviewService.updateReview(created.id(), updateReq, userDetails(user));

		assertThat(updated.text()).isEqualTo("수정된 텍스트");
		assertThat(updated.rating()).isEqualTo((short) 5);
	}

	@Test
	@DisplayName("리뷰 오너가 아닌 유저가 수정하면 예외가 발생한다")
	void updateReview_throwsForbidden_whenNotOwner() {
		ReviewCreateRequest createReq = new ReviewCreateRequest(content.getId(), "내 리뷰", (short) 4);
		ReviewDto created = reviewService.createReview(createReq, userDetails(user));

		User otherUser = createUser("다른유저", "other-" + UUID.randomUUID() + "@test.com");

		ReviewUpdateRequest updateReq = new ReviewUpdateRequest("해킹 시도", (short) 1);

		assertThatThrownBy(() -> reviewService.updateReview(created.id(), updateReq, userDetails(otherUser)))
			.isInstanceOf(ReviewException.class)
			.satisfies(ex -> assertThat(((ReviewException) ex).getErrorCode())
				.isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN));
	}

	@Test
	@DisplayName("리뷰 오너가 삭제하면 soft delete된다")
	void deleteReview_softDeletes_whenOwner() {
		ReviewCreateRequest createReq = new ReviewCreateRequest(content.getId(), "삭제 대상", (short) 2);
		ReviewDto created = reviewService.createReview(createReq, userDetails(user));

		reviewService.deleteReview(created.id(), userDetails(user));

		Review deleted = reviewRepository.findById(created.id()).orElseThrow();
		assertThat(deleted.getDeletedAt()).isNotNull();
		assertThat(reviewRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
	}

	@Test
	@DisplayName("리뷰 오너가 아닌 유저가 삭제하면 예외가 발생한다")
	void deleteReview_throwsForbidden_whenNotOwner() {
		ReviewCreateRequest createReq = new ReviewCreateRequest(content.getId(), "내 리뷰", (short) 5);
		ReviewDto created = reviewService.createReview(createReq, userDetails(user));

		User otherUser = createUser("다른유저", "other-" + UUID.randomUUID() + "@test.com");

		assertThatThrownBy(() -> reviewService.deleteReview(created.id(), userDetails(otherUser)))
			.isInstanceOf(ReviewException.class)
			.satisfies(ex -> assertThat(((ReviewException) ex).getErrorCode())
				.isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN));
	}

	@Test
	@DisplayName("리뷰 삭제 후 평점을 재계산하면 삭제된 리뷰는 제외된다")
	void refreshRatingAggregate_excludesDeletedReviews() {
		// 3명의 유저가 리뷰 작성 (rating: 5, 3, 1)
		User user2 = createUser("유저2", "u2-" + UUID.randomUUID() + "@test.com");
		User user3 = createUser("유저3", "u3-" + UUID.randomUUID() + "@test.com");

		reviewService.createReview(new ReviewCreateRequest(content.getId(), "별 다섯", (short) 5), userDetails(user));
		ReviewDto review2 = reviewService.createReview(
			new ReviewCreateRequest(content.getId(), "별 셋", (short) 3), userDetails(user2));
		reviewService.createReview(new ReviewCreateRequest(content.getId(), "별 하나", (short) 1), userDetails(user3));

		// 평점 재계산 → (5+3+1)/3 = 3.00
		entityManager.flush();
		entityManager.clear();
		contentRepository.refreshRatingAggregate(content.getId());
		entityManager.flush();
		entityManager.clear();
		Content refreshed = contentRepository.findById(content.getId()).orElseThrow();
		assertThat(refreshed.getAverageRating()).isEqualByComparingTo(new BigDecimal("3.00"));
		assertThat(refreshed.getReviewCount()).isEqualTo(3);

		// rating=3 리뷰 삭제 후 재계산 → (5+1)/2 = 3.00
		reviewService.deleteReview(review2.id(), userDetails(user2));
		entityManager.flush();
		entityManager.clear();
		contentRepository.refreshRatingAggregate(content.getId());
		entityManager.flush();
		entityManager.clear();
		Content afterDelete = contentRepository.findById(content.getId()).orElseThrow();
		assertThat(afterDelete.getAverageRating()).isEqualByComparingTo(new BigDecimal("3.00"));
		assertThat(afterDelete.getReviewCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("삭제된 콘텐츠에 리뷰를 작성하면 예외가 발생한다")
	void createReview_throwsException_whenContentDeleted() {
		content.markDeleted(Instant.now());
		contentRepository.save(content);

		ReviewCreateRequest request = new ReviewCreateRequest(content.getId(), "리뷰", (short) 3);

		assertThatThrownBy(() -> reviewService.createReview(request, userDetails(user)))
			.isInstanceOf(com.team04.mopl.content.exception.ContentException.class);
	}

	private MoplUserDetails userDetails(User user) {
		return MoplUserDetails.authenticated(user.getId(), user.getEmail(), UserRole.USER);
	}

	private User createUser(String name, String email) {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO users (id, name, email, email_type, role, is_locked, created_at, updated_at)
			VALUES (?, ?, ?, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""", userId, name, email);
		return userRepository.findById(userId).orElseThrow();
	}
}
