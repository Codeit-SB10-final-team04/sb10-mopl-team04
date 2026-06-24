package com.team04.mopl.content.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

// 없으면 플레이리스트 구현에 진행이 거의 불가능해 미리 구현했습니다...
public record ContentSummary(

	UUID id,
	ContentType type,
	String title,
	String description,
	String thumbnailUrl,
	List<String> tags,
	BigDecimal averageRating,
	long reviewCount
) {
}
