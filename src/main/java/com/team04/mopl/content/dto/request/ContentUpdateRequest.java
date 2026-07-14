package com.team04.mopl.content.dto.request;

import java.util.List;

import jakarta.annotation.Nullable;

public record ContentUpdateRequest(
	@Nullable
	String title,

	@Nullable
	String description,

	@Nullable
	List<String> tags
) {
}
