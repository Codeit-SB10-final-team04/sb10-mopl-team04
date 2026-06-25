package com.team04.mopl.follow.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record FollowRequest(
	@NotNull(message = "팔로우 하고자 하는 사람의 ID를 입력해주세요.")
	UUID followeeId
) {
}
