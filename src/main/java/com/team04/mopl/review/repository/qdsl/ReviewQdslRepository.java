package com.team04.mopl.review.repository.qdsl;

import com.team04.mopl.review.dto.request.ReviewPageRequest;
import com.team04.mopl.review.dto.response.ReviewCursorPage;

public interface ReviewQdslRepository {

	ReviewCursorPage getReviews(ReviewPageRequest request);
}
