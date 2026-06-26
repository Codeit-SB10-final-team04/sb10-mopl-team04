package com.team04.mopl.common.dto;

import java.util.List;

public record CursorPageResponse<T>(
	List<T> data,
	String nextCursor,
	String nextIdAfter,
	Boolean hasNext,
	Long totalCount,
	String sortBy,
	String sortDirection
) {
}
