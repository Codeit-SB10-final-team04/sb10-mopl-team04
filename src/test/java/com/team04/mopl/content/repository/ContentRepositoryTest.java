package com.team04.mopl.content.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
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
}
