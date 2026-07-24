package com.team04.mopl.playlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(

	@Schema(description = "플레이리스트 제목")
	@Size(max = 100, message = "플레이리스트 제목은 100자를 초과할 수 없습니다.")
	String title,

	@Schema(description = "플레이리스트 설명")
	String description
) {
}
