package com.team04.mopl.review.entity;

import java.time.Instant;
import java.util.Objects;

import com.team04.mopl.common.entity.BaseUpdatableEntity;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseUpdatableEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, columnDefinition = "UUID")
	private User user;

	// 콘텐츠
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "content_id", nullable = false, columnDefinition = "UUID")
	private Content content;

	// 리뷰 내용
	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	// 평점 (1~5점)
	@Column(nullable = false)
	private short rating;

	// 삭제일
	@Column
	private Instant deletedAt;

	public void update(String text, short rating) {
		this.text = text;
		this.rating = rating;
	}

	@Builder
	protected Review(User user, Content content, String text, short rating) {
		this.user = user;
		this.content = content;
		this.text = text;
		this.rating = rating;
		this.deletedAt = null;
	}

	public void markDeleted(Instant deletedAt) {
		if (this.deletedAt == null) {
			this.deletedAt = Objects.requireNonNull(deletedAt);
		}
	}
}
