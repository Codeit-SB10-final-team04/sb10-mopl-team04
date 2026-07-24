package com.team04.mopl.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
	@Schema(description = "이름")
	@NotBlank(message = "이름은 필수입니다.")
	@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
	String name,

	@Schema(description = "이메일")
	@NotBlank(message = "이메일은 필수입니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
	String email,

	@Schema(description = "비밀번호")
	@NotBlank(message = "비밀번호는 필수입니다.")
	@Size(min = 8, max = 255, message = "비밀번호는 8자 이상 255자 이하여야 합니다.")
	String password
) {
}
