package com.team04.mopl.playlist.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaylistDto(

	@Schema(description = "플레이리스트 ID")
	UUID id,
	@Schema(description = "플레이리스트 소유자")
	UserSummary owner,
	@Schema(description = "플레이리스트 제목")
	String title,
	@Schema(description = "플레이리스트 설명")
	String description,
	@Schema(description = "수정된 시간")
	Instant updatedAt,
	@Schema(description = "구독자 수")
	Long subscriberCount,
	@Schema(description = "구독 여부")
	Boolean subscribedByMe,
	@Schema(description = "플레이리스트에 포함된 콘텐츠 목록")
	List<ContentSummary> contents
) {
}
