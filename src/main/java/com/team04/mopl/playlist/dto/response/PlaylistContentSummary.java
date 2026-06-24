package com.team04.mopl.playlist.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.content.entity.ContentType;

// TODO: ContentSummary 구현 후 삭제
public record PlaylistContentSummary(

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
