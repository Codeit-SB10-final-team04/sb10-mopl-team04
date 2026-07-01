package com.team04.mopl.content.repository;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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
import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
class ContentRepositoryTest {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ========== existsByExternalIdAndSource ==========

    @Test
    @DisplayName("동일한 externalId + source의 Content가 존재하면 true를 반환한다")
    void existsByExternalIdAndSource_returnsTrue_whenSameExternalIdAndSourceExist() {
        // given
        contentRepository.save(Content.builder()
            .externalId("idEvent-001")
            .source(CollectionSource.SPORTS_DB)
            .title("Arsenal vs Chelsea")
            .type(ContentType.sport)
            .description("FA Cup Final")
            .thumbnailUrl("")
            .build());

        // when
        boolean result = contentRepository.existsByExternalIdAndSource("idEvent-001", CollectionSource.SPORTS_DB);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("externalId가 같아도 source가 다르면 false를 반환한다")
    void existsByExternalIdAndSource_returnsFalse_whenSourceIsDifferent() {
        // given
        contentRepository.save(Content.builder()
            .externalId("12345")
            .source(CollectionSource.TMDB)
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("")
            .thumbnailUrl("")
            .build());

        // when
        boolean result = contentRepository.existsByExternalIdAndSource("12345", CollectionSource.SPORTS_DB);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 externalId + source 조합은 false를 반환한다")
    void existsByExternalIdAndSource_returnsFalse_whenContentDoesNotExist() {
        // when
        boolean result = contentRepository.existsByExternalIdAndSource("not-exist", CollectionSource.TMDB);

        // then
        assertThat(result).isFalse();
    }

    // ========== refreshRatingAggregate ==========

    @Test
    @DisplayName("리뷰가 여러 개 있을 때 averageRating과 reviewCount가 정확히 집계된다")
    void refreshRatingAggregate_updatesAverageRatingAndCount_whenReviewsExist() {
        // given
        Content content = contentRepository.save(Content.builder()
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("우주 탐험")
            .thumbnailUrl("")
            .build());

        em.flush();

        // users 테이블은 H2 NAMED_ENUM 미지원으로 생성 실패 → FK 제약도 없으므로 user_id는 임의 UUID 사용
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), userId, content.getId(), "좋아요", (short)4);
        jdbcTemplate.update("""
                INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), userId, content.getId(), "최고에요", (short)5);

        em.clear();

        // when
        int updated = contentRepository.refreshRatingAggregate(content.getId());

        // then
        assertThat(updated).isEqualTo(1);

        Content result = contentRepository.findById(content.getId()).orElseThrow();
        assertThat(result.getReviewCount()).isEqualTo(2L);
        assertThat(result.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
    }

    @Test
    @DisplayName("소프트 딜리트된 리뷰는 집계에서 제외된다")
    void refreshRatingAggregate_excludesSoftDeletedReviews() {
        // given
        Content content = contentRepository.save(Content.builder()
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("우주 탐험")
            .thumbnailUrl("")
            .build());

        em.flush();

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), userId, content.getId(), "좋아요", (short)5);
        jdbcTemplate.update("""
                INSERT INTO content_reviews (id, user_id, content_id, text, rating, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), userId, content.getId(), "별로에요", (short)1,
            Timestamp.from(Instant.now()));

        em.clear();

        // when
        contentRepository.refreshRatingAggregate(content.getId());

        // then
        Content result = contentRepository.findById(content.getId()).orElseThrow();
        assertThat(result.getReviewCount()).isEqualTo(1L);
        assertThat(result.getAverageRating()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("리뷰가 없으면 averageRating=0.0, reviewCount=0으로 업데이트된다")
    void refreshRatingAggregate_setsZero_whenNoReviews() {
        // given
        Content content = contentRepository.save(Content.builder()
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("우주 탐험")
            .thumbnailUrl("")
            .build());

        em.flush();
        em.clear();

        // when
        int updated = contentRepository.refreshRatingAggregate(content.getId());

        // then
        assertThat(updated).isEqualTo(1);

        Content result = contentRepository.findById(content.getId()).orElseThrow();
        assertThat(result.getReviewCount()).isEqualTo(0L);
        assertThat(result.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("소프트 딜리트된 콘텐츠는 업데이트되지 않아 반환값이 0이다")
    void refreshRatingAggregate_returnsZero_whenContentSoftDeleted() {
        // given
        Content content = contentRepository.save(Content.builder()
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("우주 탐험")
            .thumbnailUrl("")
            .build());

        em.flush();

        jdbcTemplate.update(
            "UPDATE contents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
            content.getId());

        em.clear();

        // when
        int updated = contentRepository.refreshRatingAggregate(content.getId());

        // then
        assertThat(updated).isEqualTo(0);
    }
}
