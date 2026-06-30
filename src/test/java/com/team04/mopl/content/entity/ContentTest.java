package com.team04.mopl.content.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentTest {

    private Content content;

    @BeforeEach
    void setUp() {
        content = Content.builder()
            .title("인터스텔라")
            .type(ContentType.movie)
            .description("우주 탐험 이야기")
            .thumbnailUrl("https://example.com/old.jpg")
            .build();
    }

    // ========== updateTitle ==========

    @Test
    @DisplayName("title이 null이 아니면 변경된다")
    void updateTitle_changesTitle_whenNotNull() {
        content.updateTitle("듄");

        assertThat(content.getTitle()).isEqualTo("듄");
    }

    @Test
    @DisplayName("title이 null이면 기존 값이 유지된다")
    void updateTitle_keepsOriginal_whenNull() {
        content.updateTitle(null);

        assertThat(content.getTitle()).isEqualTo("인터스텔라");
    }

    // ========== updateDescription ==========

    @Test
    @DisplayName("description이 null이 아니면 변경된다")
    void updateDescription_changesDescription_whenNotNull() {
        content.updateDescription("사막 행성 이야기");

        assertThat(content.getDescription()).isEqualTo("사막 행성 이야기");
    }

    @Test
    @DisplayName("description이 null이면 기존 값이 유지된다")
    void updateDescription_keepsOriginal_whenNull() {
        content.updateDescription(null);

        assertThat(content.getDescription()).isEqualTo("우주 탐험 이야기");
    }

    // ========== updateThumbnailUrl ==========

    @Test
    @DisplayName("thumbnailUrl이 null이 아니면 변경된다")
    void updateThumbnailUrl_changesUrl_whenNotNull() {
        content.updateThumbnailUrl("https://example.com/new.jpg");

        assertThat(content.getThumbnailUrl()).isEqualTo("https://example.com/new.jpg");
    }

    @Test
    @DisplayName("thumbnailUrl이 null이면 기존 값이 유지된다")
    void updateThumbnailUrl_keepsOriginal_whenNull() {
        content.updateThumbnailUrl(null);

        assertThat(content.getThumbnailUrl()).isEqualTo("https://example.com/old.jpg");
    }
}
