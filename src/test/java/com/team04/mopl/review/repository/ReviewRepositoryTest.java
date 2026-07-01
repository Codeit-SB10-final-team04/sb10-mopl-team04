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
import org.springframework.test.context.ActiveProfiles;

import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
class ReviewRepositoryTest {

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private TestEntityManager em;

	private User user;
	private Content content;

	@BeforeEach
	void setUp() {
		user = em.persist(User.builder()
			.name("테스트유저")
			.email("test@test.com")
			.role(UserRole.USER)
			.build());

		content = em.persist(Content.builder()
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
		em.persist(Review.builder()
			.user(user)
			.content(content)
			.text("재밌어요")
			.rating((short)5)
			.build());
		em.flush();

		// when
		boolean result = reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(
			user.getId(), content.getId());

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("리뷰가 소프트 딜리트된 경우 false를 반환한다")
	void existsByUserIdAndContentIdAndDeletedAtIsNull_returnsFalse_whenReviewSoftDeleted() {
		// given
		Review review = em.persist(Review.builder()
			.user(user)
			.content(content)
			.text("재밌어요")
			.rating((short)5)
			.build());

		em.getEntityManager()
			.createQuery("UPDATE Review r SET r.deletedAt = :now WHERE r.id = :id")
			.setParameter("now", Instant.now())
			.setParameter("id", review.getId())
			.executeUpdate();
		em.flush();

		// when
		boolean result = reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(
			user.getId(), content.getId());

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
}
