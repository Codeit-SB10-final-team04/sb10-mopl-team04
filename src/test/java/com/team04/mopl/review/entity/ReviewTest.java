package com.team04.mopl.review.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.user.entity.User;

class ReviewTest {

	@Test
	@DisplayName("update 호출 시 text와 rating이 변경된다")
	void update_changesTextAndRating() {
		// given
		Review review = Review.builder()
			.user(mock(User.class))
			.content(mock(Content.class))
			.text("원본 리뷰")
			.rating((short)3)
			.build();

		// when
		review.update("수정된 리뷰", (short)5);

		// then
		assertThat(review.getText()).isEqualTo("수정된 리뷰");
		assertThat(review.getRating()).isEqualTo((short)5);
	}

	@Test
	@DisplayName("markDeleted 호출 시 deletedAt이 설정된다")
	void markDeleted_setsDeletedAt() {
		// given
		Review review = Review.builder()
			.user(mock(User.class))
			.content(mock(Content.class))
			.text("리뷰")
			.rating((short)3)
			.build();

		Instant now = Instant.now();

		// when
		review.markDeleted(now);

		// then
		assertThat(review.getDeletedAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("이미 삭제된 리뷰에 markDeleted를 다시 호출하면 deletedAt이 변경되지 않는다")
	void markDeleted_doesNotChange_whenAlreadyDeleted() {
		// given
		Review review = Review.builder()
			.user(mock(User.class))
			.content(mock(Content.class))
			.text("리뷰")
			.rating((short)3)
			.build();

		Instant firstDeletedAt = Instant.now();
		review.markDeleted(firstDeletedAt);

		// when
		review.markDeleted(Instant.now().plusSeconds(100));

		// then
		assertThat(review.getDeletedAt()).isEqualTo(firstDeletedAt);
	}

	@Test
	@DisplayName("markDeleted에 null을 전달하면 NullPointerException이 발생한다")
	void markDeleted_throwsNpe_whenNullPassed() {
		// given
		Review review = Review.builder()
			.user(mock(User.class))
			.content(mock(Content.class))
			.text("리뷰")
			.rating((short)3)
			.build();

		// when & then
		assertThatThrownBy(() -> review.markDeleted(null))
			.isInstanceOf(NullPointerException.class);
	}
}
