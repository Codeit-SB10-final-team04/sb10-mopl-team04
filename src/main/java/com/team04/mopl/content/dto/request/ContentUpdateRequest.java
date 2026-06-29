package com.team04.mopl.content.dto.request;

import java.util.List;

public record ContentUpdateRequest(
	String title,
	String description,
	List<String> tags
) {
}
