package com.team04.mopl.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(

	@NotBlank(message = "플레이리스트 제목을 입력해 주세요.")
	@Size(min = 1, max = 100, message = "플레이리스트 제목은 100자 이내로 입력해 주세요")
	String title,

	@NotBlank(message = "플레이리스트 설명을 입력해 주세요.")
	String description
) {
}
