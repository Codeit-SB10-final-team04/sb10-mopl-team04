package com.team04.mopl.content.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ContentDto(
	@Schema(description = "콘텐츠 ID")
	UUID id,
	@Schema(description = "콘텐츠 타입")
	ContentType type,
	@Schema(description = "콘텐츠 제목")
	String title,
	@Schema(description = "콘텐츠 설명")
	String description,
	@Schema(description = "썸네일 이미지 URL")
	String thumbnailUrl,
	@Schema(description = "콘텐츠 태그 목록")
	List<String> tags,
	@Schema(description = "평균 평점")
	BigDecimal averageRating,
	@Schema(description = "리뷰 개수")
	Long reviewCount,
	@Schema(description = "시청자 수")
	Long watcherCount
) {
}
