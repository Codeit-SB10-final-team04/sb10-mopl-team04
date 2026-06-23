package com.team04.mopl.review.entity;

import com.team04.mopl.common.entity.BaseUpdatableEntity;
import com.team04.mopl.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "content_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseUpdatableEntity {
    // Todo: User 생성 후 변경
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id", nullable = false, columnDefinition = "UUID")
    // private User user;

    // 콘텐츠
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false, columnDefinition = "UUID")
    private Content content;

    // 리뷰 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    // 평점 (0~5점)
    @Column(nullable = false)
    private short rating;

    // 삭제일
    @Column
    private Instant deletedAt;

    @Builder
    protected Review(Content content, String text, short rating) {
        this.content = content;
        this.text = text;
        this.rating = rating;
        this.deletedAt = null;
    }
}
