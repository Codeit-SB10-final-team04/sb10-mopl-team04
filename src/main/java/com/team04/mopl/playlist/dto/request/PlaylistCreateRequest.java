package com.team04.mopl.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(

	@NotBlank(message = "플레이리스트 제목이 입력되지 않았습니다.")
	@Size(min = 1, max = 100, message = "플레이리스트 제목은 1~100 글자 이상이어야 합니다.")
	String title,

	@NotBlank(message = "플레이리스트 설명이 입력되지 않았습니다.")
	@Size(min = 1, message = "플레이리스트 설명은 1 글자 이상이어야 합니다.")
	String description
) {
}
