package com.team04.mopl.directmessage.dto.request;

import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

public record DirectMessagePagedRequest(
	// 메인 커서
	String cursor,

	// 보조 커서
	UUID idAfter,

	// 페이지 개수
	Integer limit,

	// 정렬 방향
	SortDirection sortDirection,

	// 정렬 기준
	String sortBy
) {
	// 기본값 설정을 위한 생성자
	public DirectMessagePagedRequest {
		// 페이지 내 요소 개수 기본값: 10
		if (limit == null) {
			limit = 10;
		} else if (limit <= 0) {
			throw new IllegalArgumentException("limit은 0보다 커야 합니다.");
		}

		// 정렬 방향 기본값: DESC
		sortDirection = (sortDirection == null)
			? SortDirection.DESCENDING
			: sortDirection;

		// 정렬 기준 기본값: createdAt
		sortBy = (sortBy == null || sortBy.isBlank())
			? "createdAt"
			: sortBy;
	}
}
