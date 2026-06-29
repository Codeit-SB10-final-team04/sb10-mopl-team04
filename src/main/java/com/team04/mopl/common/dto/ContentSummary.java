package com.team04.mopl.common.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

public record ContentSummary(

	UUID id,
	ContentType type,
	String title,
	String description,
	String thumbnailUrl,
	List<String> tags,
	BigDecimal averageRating,
	Long reviewCount
) {
}
