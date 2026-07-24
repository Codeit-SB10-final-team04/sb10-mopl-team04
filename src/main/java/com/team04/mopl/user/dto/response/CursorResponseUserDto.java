package com.team04.mopl.user.dto.response;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

import io.swagger.v3.oas.annotations.media.Schema;

public record CursorResponseUserDto(
	@Schema(description = "데이터 목록")
	List<UserDto> data,

	@Schema(description = "다음 커서")
	String nextCursor,

	@Schema(description = "다음 요청의 보조 커서")
	UUID nextIdAfter,

	@Schema(description = "다음 데이터가 있는지 여부")
	Boolean hasNext,

	@Schema(description = "총 데이터 개수")
	Long totalCount,

	@Schema(description = "정렬 기준")
	String sortBy,

	@Schema(description = "정렬 방향")
	SortDirection sortDirection
) {

	public CursorResponseUserDto {
		data = data == null
			? List.of()
			: List.copyOf(data);
	}
}
