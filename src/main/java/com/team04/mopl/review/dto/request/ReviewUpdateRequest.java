package com.team04.mopl.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewUpdateRequest(
	@NotBlank
	@Size(max = 500)
	String text,

	@NotNull
	@Min(1) @Max(5)
	Short rating
) {
}
