package com.team04.mopl.user.dto.request;

import com.team04.mopl.user.entity.UserRole;

import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
	@NotNull(message = "권한은 필수입니다.")
	UserRole role
) {
}
