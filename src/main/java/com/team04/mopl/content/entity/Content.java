package com.team04.mopl.content.entity;

import com.team04.mopl.common.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseUpdatableEntity {
    // 제목
    @Column(nullable = false, length = 200)
    private String title;

    // 컨텐츠 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ContentType type;

    // 설명
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // 썸네일 URL
    @Column(nullable = false, length = 500)
    private String thumbnailUrl;

    // 평균 평점 - 5점 만점
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    // 리뷰 개수
    @Column(nullable = false)
    private long reviewerCount;

    // 시청자 수
    @Column(nullable = false)
    private long watcherCount;

    // 삭제일
    @Column
    private Instant deletedAt;

    @Builder
    protected Content(
            String title,
            ContentType type,
            String description,
            String thumbnailUrl
    ) {
        this.title = title;
        this.type = type;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.averageRating = BigDecimal.ZERO;
        this.reviewerCount = 0L;
        this.watcherCount = 0L;
        this.deletedAt = null;
    }
}
