package com.team04.mopl.content.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ContentCreateRequest(
	@Schema(description = "콘텐츠 타입")
	@NotBlank(message = "콘텐츠 타입은 필수입니다.")
	String type,

	@Schema(description = "콘텐츠 제목")
	@NotBlank(message = "콘텐츠 제목은 필수입니다.")
	String title,

	@Schema(description = "콘텐츠 설명")
	@NotBlank(message = "콘텐츠 설명은 필수입니다.")
	String description,

	@Schema(description = "콘텐츠 태그 목록")
	List<String> tags
) {
}
