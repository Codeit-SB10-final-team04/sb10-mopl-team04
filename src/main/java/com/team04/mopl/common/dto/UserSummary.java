package com.team04.mopl.common.dto;

import java.util.UUID;

public record UserSummary(

	UUID userId,
	String name,
	String profileImageUrl
) {
}
