package com.team04.mopl.directmessage.dto.request;

import java.util.UUID;

import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;

public record DirectMessagePageRequest(
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
	public DirectMessagePageRequest {
		// 유효성 검증: 입력값 필수 여부
		validateCursorRequest(cursor, idAfter);

		// 페이지 내 요소 개수 기본값 및 유효성 검증 (1 ~ 100)
		if (limit == null) {
			limit = 50;
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

	private void validateCursorRequest(String cursor, UUID idAfter) {
		boolean hasCursor = StringUtils.hasText(cursor);
		boolean hasIdAfter = idAfter != null;

		// 둘 중 하나만 존재할 경우 예외 발생
		if (hasCursor ^ hasIdAfter) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT)
				.addDetail("cursor", cursor != null ? cursor : "null")
				.addDetail("idAfter", idAfter != null ? idAfter.toString() : "null");
		}
	}
}
