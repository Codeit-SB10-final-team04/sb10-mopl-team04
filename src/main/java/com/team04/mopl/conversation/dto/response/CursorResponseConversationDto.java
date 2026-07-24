package com.team04.mopl.conversation.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record CursorResponseConversationDto(
	@Schema(description = "데이터 목록")
	List<ConversationDto> data,

	@Schema(description = "다음 커서")
	String nextCursor,

	@Schema(description = "다음 요청의 보조 커서")
	UUID nextIdAfter,

	@Schema(description = "다음 데이터가 있는지 여부")
	boolean hasNext,

	@Schema(description = "총 데이터 개수")
	Long totalCount,

	@Schema(description = "정렬 기준")
	String sortBy,

	@Schema(description = "정렬 방향")
	String sortDirection
) {
}
