package com.team04.mopl.playlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(

	@Schema(description = "플레이리스트 제목")
	@NotBlank(message = "플레이리스트 제목을 입력해 주세요.")
	@Size(min = 1, max = 100, message = "플레이리스트 제목은 100자 이내로 입력해 주세요")
	String title,

	@Schema(description = "플레이리스트 설명")
	@NotBlank(message = "플레이리스트 설명을 입력해 주세요.")
	String description
) {
}
