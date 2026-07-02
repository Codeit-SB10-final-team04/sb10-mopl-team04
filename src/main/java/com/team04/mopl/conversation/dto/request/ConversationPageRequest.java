package com.team04.mopl.conversation.dto.request;

import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConversationPageRequest(
	// 검색 키워드: 사용자 이름, 메시지 내용
	String keywordLike,

	// 메인 커서
	String cursor,

	// 보조 커서
	UUID idAfter,

	// 페이지 개수
	@NotNull(message = "조회하고자 하는 대화방 개수를 입력해주세요.")
	@Positive(message = "조회하고자 하는 대화방 개수는 양수이어야 합니다.")
	@Max(value = 50, message = "한 번에 조회할 수 있는 대화방은 최대 50개입니다.")
	Integer limit,

	// 정렬 방향
	@NotNull(message = "정렬 방향을 선택해주세요.")
	SortDirection sortDirection,

	// 정렬 기준
	@NotBlank(message = "정렬 기준을 선택해주세요.")
	String sortBy
) {
	// 기본값 설정을 위한 생성자
	public ConversationPageRequest {
		// 페이지 내 요소 개수 기본값: 10
		limit = (limit == null || limit <= 0)
			? 10
			: limit;

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
