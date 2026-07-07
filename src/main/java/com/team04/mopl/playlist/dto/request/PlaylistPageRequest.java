package com.team04.mopl.playlist.dto.request;

import java.util.UUID;

import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.playlist.enums.PlaylistSortBy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaylistPageRequest(

	String keywordLike,

	UUID ownerIdEqual,

	UUID subscriberIdEqual,

	String cursor,

	UUID idAfter,

	@NotNull(message = "조회할 데이터 개수는 필수 입니다.")
	@Min(value = 1, message = "조회할 데이터 개수는 1 개 이상이어야 합니다.")
	@Max(value = 100, message = "조회할 데이터 개수는 100 개를 초과할 수 없습니다.")
	Integer limit,

	@NotNull(message = "정렬 방향은 필수 입니다.")
	SortDirection sortDirection,

	@NotNull(message = "정렬 기준은 필수 입니다.")
	PlaylistSortBy sortBy
) {
	public String normalizedKeyword() {
		// keywordLike가 null, 공백, 탭, 줄바꿈 등이 있는 문자열이면 -> false
		return StringUtils.hasText(keywordLike)
			? keywordLike.strip()
			: null;
	}

	@AssertTrue(message = "cursor와 idAfter는 함께 요청되어야 합니다.")
	public boolean isCursorAndIdAfterPaired() {
		return (cursor == null) == (idAfter == null);
	}
}
