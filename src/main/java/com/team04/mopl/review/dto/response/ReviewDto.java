package com.team04.mopl.review.dto.response;

import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReviewDto(
	@Schema(description = "리뷰 ID")
	UUID id,

	@Schema(description = "콘텐츠 ID")
	UUID contentId,

	@Schema(description = "작성자 정보")
	UserSummary author,

	@Schema(description = "리뷰 내용")
	String text,

	@Schema(description = "평점 (0.0 ~ 5.0)")
	Short rating
) {
}
