package com.team04.mopl.common.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record CursorResponse<T>(
	@Schema(description = "데이터 목록")
	List<T> data,
	@Schema(description = "다음 커서")
	String nextCursor,
	@Schema(description = "다음 요청의 보조 커서")
	String nextIdAfter,
	@Schema(description = "다음 데이터가 있는지 여부")
	Boolean hasNext,
	@Schema(description = "총 데이터 개수")
	Long totalCount,
	@Schema(description = "정렬 기준")
	String sortBy,
	@Schema(description = "정렬 방향")
	String sortDirection
) {
}
