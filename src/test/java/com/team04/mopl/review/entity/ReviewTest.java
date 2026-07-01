package com.team04.mopl.review.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
}
