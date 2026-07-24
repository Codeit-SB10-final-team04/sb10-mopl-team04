package com.team04.mopl.review.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
	@Schema(description = "콘텐츠 ID")
	@NotNull
	UUID contentId,

	@Schema(description = "리뷰 내용")
	@NotBlank
	@Size(max = 500)
	String text,

	@Schema(description = "평점 (0.0 ~ 5.0)")
	@NotNull
	@Min(1) @Max(5)
	Short rating
) {
}
