package com.team04.mopl.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
	@Schema(description = "이름")
	@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
	String name
) {
}
