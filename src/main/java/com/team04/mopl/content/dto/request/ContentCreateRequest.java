package com.team04.mopl.content.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ContentCreateRequest(
	@NotBlank(message = "콘텐츠 타입은 필수입니다.")
	String type,

	@NotBlank(message = "콘텐츠 제목은 필수입니다.")
	String title,

	@NotBlank(message = "콘텐츠 설명은 필수입니다.")
	String description,

	List<String> tags
) {
}
