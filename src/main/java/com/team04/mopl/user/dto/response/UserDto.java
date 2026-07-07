package com.team04.mopl.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.user.entity.UserRole;

public record UserDto(
	UUID id,
	Instant createdAt,
	String email,
	String name,
	String profileImageUrl,
	UserRole role,
	Boolean locked
) {
}
