package com.team04.mopl.review.dto.request;

public record ReviewUpdateRequest(
	String text,
	Short rating
) {
}
