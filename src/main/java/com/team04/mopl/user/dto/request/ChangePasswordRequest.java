package com.team04.mopl.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
	@Schema(description = "새 비밀번호")
	@NotBlank(message = "비밀번호는 필수입니다.")
	@Size(min = 8, max = 255, message = "비밀번호는 8자 이상 255자 이하여야 합니다.")
	String password
) {
}
