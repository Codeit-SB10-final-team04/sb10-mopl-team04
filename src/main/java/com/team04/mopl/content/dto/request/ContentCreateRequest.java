package com.team04.mopl.content.dto.request;

import java.util.List;

public record ContentCreateRequest(
	String type,
	String title,
	String descriptions,
	List<String> tags
) {
}
