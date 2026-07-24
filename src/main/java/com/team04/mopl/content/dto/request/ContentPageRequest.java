package com.team04.mopl.content.dto.request;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ContentPageRequest(
	@Schema(description = "콘텐츠 타입 필터")
	String typeEqual,
	@Schema(description = "검색 키워드")
	String keywordLike,
	@Schema(description = "태그 필터 목록")
	List<String> tagsIn,
	@Schema(description = "주 커서")
	String cursor,
	@Schema(description = "보조 커서")
	UUID idAfter,
	@Schema(description = "조회할 데이터 개수")
	@Min(1)
	@Max(100)
	Integer limit,
	@Schema(description = "정렬 방향")
	String sortDirection,
	@Schema(description = "정렬 기준")
	String sortBy
) {
	public ContentPageRequest {
		if (sortBy == null || sortBy.isBlank()) {
			sortBy = "watcherCount";
		} else if ("rate".equals(sortBy)) {
			sortBy = "averageRating";
		}
		if (sortDirection == null || sortDirection.isBlank()) {
			sortDirection = "DESCENDING";
		}
	}
}
