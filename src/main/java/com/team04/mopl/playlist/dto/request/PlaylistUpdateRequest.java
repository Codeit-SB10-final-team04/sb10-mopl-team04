package com.team04.mopl.playlist.dto.request;

import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(

	@Size(max = 100, message = "플레이리스트 제목은 100자를 초과할 수 없습니다.")
	String title,

	String description
) {
}
