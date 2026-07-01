package com.team04.mopl.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewUpdateRequest(
	@NotBlank
	String text,

	@Min(1) @Max(5)
	Short rating
) {
}
