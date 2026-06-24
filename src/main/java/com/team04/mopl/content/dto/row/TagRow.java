package com.team04.mopl.content.dto.row;

import java.util.UUID;

// 콘텐츠 id별 태그명 일괄 조회하기 위한 Repository 조회용 DTO
public record TagRow(

	UUID contentId,
	String tagName
) {
}
