package com.team04.mopl.directmessage.dto.request;

import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;

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
		// 페이지 내 요소 개수 기본값 및 유효성 검증 (1 ~ 100)
		if (limit == null) {
			limit = 10;
		} else if (limit <= 0 || limit > 100) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT)
				.addDetail("limit", String.valueOf(limit));
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
