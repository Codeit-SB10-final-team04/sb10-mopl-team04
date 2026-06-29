package com.team04.mopl.auth.dto.response;

import com.team04.mopl.user.dto.response.UserDto;

public record JwtDto(
	UserDto userDto,
	String accessToken
) {
}
