package com.team04.mopl.content.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ContentUpdateRequest(
	@NotBlank(message = "제목은 공백일 수 없습니다.")
	String title,

	@NotBlank(message = "설명은 공백일 수 없습니다.")
	String description,
	List<String> tags
) {
}
