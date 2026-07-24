package com.team04.mopl.follow.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record FollowDto(
	@Schema(description = "팔로우 ID")
	UUID id,

	@Schema(description = "팔로우 대상 사용자 ID")
	UUID followeeId,

	@Schema(description = "팔로워 사용자 ID")
	UUID followerId
) {
}
