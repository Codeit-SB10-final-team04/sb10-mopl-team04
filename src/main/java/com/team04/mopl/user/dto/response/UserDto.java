package com.team04.mopl.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.user.entity.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserDto(
	@Schema(description = "사용자 ID")
	UUID id,
	@Schema(description = "사용자 생성 시간")
	Instant createdAt,
	@Schema(description = "이메일")
	String email,
	@Schema(description = "사용자 이름")
	String name,
	@Schema(description = "프로필 이미지 URL")
	String profileImageUrl,
	@Schema(description = "사용자 역할")
	UserRole role,
	@Schema(description = "계정 잠금 여부")
	Boolean locked
) {
}
