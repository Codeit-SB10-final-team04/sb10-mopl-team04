package com.team04.mopl.conversation.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorResponseConversationDto(
	// 대화 목록
	List<ConversationDto> data,

	// 다음 메인 커서 값
	String nextCursor,

	// 다음 보조 커서 값
	UUID nextIdAfter,

	// 다음 페이지 존재 여부
	boolean hasNext,

	// 조회한 대화 목록의 전체 개수
	Long totalCount,

	// 정렬 기준
	String sortBy,

	// 정렬 방향
	String sortDirection
) {
}
