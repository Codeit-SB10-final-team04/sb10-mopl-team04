package com.team04.mopl.watching.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;

import io.swagger.v3.oas.annotations.media.Schema;

public record WatchingSessionDto(
	@Schema(description = "시청 세션 ID")
	UUID id,

	@Schema(description = "시청 세션 생성 시간")
	Instant createdAt,

	@Schema(description = "시청자 정보")
	UserSummary watcher,

	@Schema(description = "시청 중인 콘텐츠 정보")
	ContentSummary content
) {
}
