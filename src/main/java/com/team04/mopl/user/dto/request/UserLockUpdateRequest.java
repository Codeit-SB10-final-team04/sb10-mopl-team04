package com.team04.mopl.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(
	@Schema(description = "변경할 잠금 상태")
	@NotNull(message = "잠금 상태는 필수입니다.")
	Boolean locked
) {
}
