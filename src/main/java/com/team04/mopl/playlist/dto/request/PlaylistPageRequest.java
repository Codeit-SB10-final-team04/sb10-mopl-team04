package com.team04.mopl.playlist.dto.request;

import java.util.UUID;

import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.playlist.enums.PlaylistSortBy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaylistPageRequest(

	@Schema(description = "검색 키워드")
	String keywordLike,

	@Schema(description = "소유자 ID 필터")
	UUID ownerIdEqual,

	@Schema(description = "구독자 ID 필터")
	UUID subscriberIdEqual,

	@Schema(description = "주 커서")
	String cursor,

	@Schema(description = "보조 커서")
	UUID idAfter,

	@Schema(description = "조회할 데이터 개수")
	@NotNull(message = "조회할 데이터 개수는 필수 입니다.")
	@Min(value = 1, message = "조회할 데이터 개수는 1 개 이상이어야 합니다.")
	@Max(value = 100, message = "조회할 데이터 개수는 100 개를 초과할 수 없습니다.")
	Integer limit,

	@Schema(description = "정렬 방향")
	@NotNull(message = "정렬 방향은 필수 입니다.")
	SortDirection sortDirection,

	@Schema(description = "정렬 기준")
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
