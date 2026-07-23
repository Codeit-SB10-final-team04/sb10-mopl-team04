package com.team04.mopl.common.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ContentSummary(

	@Schema(description = "콘텐츠 ID")
	UUID id,
	@Schema(description = "콘텐츠 유형")
	ContentType type,
	@Schema(description = "콘텐츠 제목")
	String title,
	@Schema(description = "콘텐츠 설명")
	String description,
	@Schema(description = "썸네일 URL")
	String thumbnailUrl,
	@Schema(description = "태그 목록")
	List<String> tags,
	@Schema(description = "평균 평점")
	BigDecimal averageRating,
	@Schema(description = "리뷰 수")
	Long reviewCount
) {
}
