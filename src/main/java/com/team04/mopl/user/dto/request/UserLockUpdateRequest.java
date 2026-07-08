package com.team04.mopl.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(
	@NotNull(message = "잠금 상태는 필수입니다.")
	Boolean locked
) {
}
