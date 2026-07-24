package com.team04.mopl.content.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

public record ContentUpdateRequest(
	@Schema(description = "콘텐츠 제목")
	@Nullable
	String title,

	@Schema(description = "콘텐츠 설명")
	@Nullable
	String description,

	@Schema(description = "콘텐츠 태그 목록")
	@Nullable
	List<String> tags
) {
}
