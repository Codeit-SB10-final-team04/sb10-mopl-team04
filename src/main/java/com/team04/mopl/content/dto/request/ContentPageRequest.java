package com.team04.mopl.content.dto.request;

import java.util.List;
import java.util.UUID;

public record ContentPageRequest(
	String typeEqual, // movie, tv_series, sports
	String keywordLike,
	List<String> tagsIn, // 안씀 근데 명세서에 있음
	String cursor, // 주 커서
	UUID idAfter, // 보조 커서
	Integer limit, // 기본값: 20
	String sortDirection, // DESCENDING
	String sortBy // 기본값: watcherCount
) {
}
