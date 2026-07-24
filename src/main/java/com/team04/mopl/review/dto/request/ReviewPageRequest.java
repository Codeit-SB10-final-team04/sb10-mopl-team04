package com.team04.mopl.review.dto.request;

import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.review.enums.ReviewSortBy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewPageRequest(

	@Schema(description = "콘텐츠 ID")
	@NotNull(message = "콘텐츠 ID는 필수입니다.")
	UUID contentId,

	@Schema(description = "커서")
	String cursor,

	@Schema(description = "보조 커서")
	UUID idAfter,

	@Schema(description = "조회할 데이터 개수")
	@NotNull(message = "조회할 데이터 개수는 필수입니다.")
	@Min(value = 1, message = "조회할 데이터 개수는 1개 이상이어야 합니다.")
	@Max(value = 100, message = "조회할 데이터 개수는 100개를 초과할 수 없습니다.")
	Integer limit,

	@Schema(description = "정렬 방향")
	@NotNull(message = "정렬 방향은 필수입니다.")
	SortDirection sortDirection,

	@Schema(description = "정렬 기준")
	@NotNull(message = "정렬 기준은 필수입니다.")
	ReviewSortBy sortBy
) {

	@AssertTrue(message = "cursor와 idAfter는 함께 요청되어야 합니다.")
	public boolean isCursorAndIdAfterPaired() {
		return (cursor == null) == (idAfter == null);
	}
}
