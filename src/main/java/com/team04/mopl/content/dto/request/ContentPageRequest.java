package com.team04.mopl.content.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ContentPageRequest(
	String typeEqual, // movie, tv_series, sports
	String keywordLike,
	List<String> tagsIn, // 안씀 근데 명세서에 있음
	String cursor, // 주 커서
	UUID idAfter, // 보조 커서
	@Min(1)
	@Max(100)
	Integer limit, // 기본값: 20
	@NotBlank(message = "정렬 방향은 필수입니다.")
	String sortDirection, // DESCENDING
	@NotBlank(message = "정렬 기준은 필수입니다.")
	String sortBy // 기본값: watcherCount
) {
}
