package com.team04.mopl.review.dto.response;

import java.util.List;

import com.team04.mopl.review.entity.Review;

public record ReviewCursorPage(

	List<Review> reviewList,
	boolean hasNext,
	long totalCount
) {

	public ReviewCursorPage {
		reviewList = (reviewList == null)
			? List.of()
			: List.copyOf(reviewList);
	}
}
