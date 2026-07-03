package com.team04.mopl.user.dto.response;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

public record CursorResponseUserDto(
	// 데이터 목록
	List<UserDto> data,

	// 다음 커서
	String nextCursor,

	// 다음 요청의 보조 커서
	UUID nextIdAfter,

	// 다음 페이지 존재 여부
	Boolean hasNext,

	// 총 데이터 개수
	Long totalCount,

	// 정렬 기준
	String sortBy,

	// 정렬 방향
	SortDirection sortDirection
) {

	public CursorResponseUserDto {
		data = data == null
			? List.of()
			: List.copyOf(data);
	}
}
