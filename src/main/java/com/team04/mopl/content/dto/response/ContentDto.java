package com.team04.mopl.content.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

public record ContentDto(
	UUID id,
	ContentType type,
	String title,
	String description,
	String thumbnailUrl,
	List<String> tags,
	BigDecimal averageRatings,
	Long reviewCount,
	Long watcherCount
) {
}
