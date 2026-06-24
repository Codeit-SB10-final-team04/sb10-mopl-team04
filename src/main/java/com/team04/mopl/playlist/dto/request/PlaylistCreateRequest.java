package com.team04.mopl.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(

	@NotBlank(message = "title이 입력되지 않았습니다.")
	@Size(max = 100, min = 1)
	String title,

	@NotBlank(message = "description이 입력되지 않았습니다.")
	@Size(min = 1)
	String description
) {
}
