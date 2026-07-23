package com.team04.mopl.follow.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FollowRequest(
	@Schema(description = "팔로우 대상 사용자 ID")
	@NotNull(message = "팔로우 하고자 하는 사람의 ID를 입력해주세요.")
	UUID followeeId
) {
}
