package com.team04.mopl.common.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSummary(

	@Schema(description = "사용자 ID")
	UUID userId,
	@Schema(description = "사용자 이름")
	String name,
	@Schema(description = "사용자 프로필 이미지 URL")
	String profileImageUrl
) {
}
