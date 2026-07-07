package com.team04.mopl.review.dto.response;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

public record CursorResponseReviewDto(

	List<ReviewDto> data,

	String nextCursor,

	UUID nextIdAfter,

	Boolean hasNext,

	Long totalCount,

	String sortBy,

	SortDirection sortDirection
) {
	public CursorResponseReviewDto {
		data = (data == null)
			? List.of()
			: List.copyOf(data);
	}
}
