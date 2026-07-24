package com.team04.mopl.user.dto.response;

import java.util.List;

public record UserCursorPage(
	// 내부 조회 사용자 목록
	List<UserDto> users,

	// 다음 페이지 존재 여부
	boolean hasNext,

	// 필터 조건 기준 전체 개수
	long totalCount
) {

	public UserCursorPage {
		users = (users == null)
			? List.of()
			: List.copyOf(users);
	}
}
