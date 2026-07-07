package com.team04.mopl.review.dto.response;

import java.util.UUID;

import com.team04.mopl.common.dto.UserSummary;

public record ReviewDto(
	UUID id,
	UUID contentId,
	UserSummary author,
	String text,
	Short rating
) {
}
