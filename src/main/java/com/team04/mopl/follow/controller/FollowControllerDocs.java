package com.team04.mopl.follow.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Follow API", description = "팔로우 관련 API")
public interface FollowControllerDocs {
	// 팔로우 생성
	// TODO: 메서드를 요청한 사용자 정보가 필요 -> 임시로 CustomUserDetails로 구현하되, 주석 처리함
	@Operation(
		summary = "팔로우 생성",
		description = "사용자가 특정 사용자를 팔로우합니다."
	)
	ResponseEntity<FollowDto> createFollow(
		FollowRequest followRequest,
		@RequestHeader("X-MOPL-USER-ID") UUID currentUserId
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "사용자의 특정 사용자 팔로우 여보 조회",
		description = "사용자가 특정 사용자에 대한 팔로우 여부를 반환합니다."
	)
	ResponseEntity<FollowDto> isFollowing(
		UUID followeeId,
		@RequestHeader("X-MOPL-USER-ID") UUID currentUserId
		// @AuthenticationPrincipal MoplUserDetails moplUserDetails
	);

	// 특정 사용자의 팔로우 수 조회
	@Operation(
		summary = "특정 사용자의 팔로우 수 조회",
		description = "특정 사용자를 팔로우 하고 있는 사용자의 수를 반환합니다."
	)
	ResponseEntity<Long> getFollowerCount(UUID followeeId);
}
