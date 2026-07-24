package com.team04.mopl.follow.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "팔로우 관리", description = "팔로우 관리 API")
public interface FollowControllerDocs {

	@Operation(
		summary = "팔로우 생성",
		description = "사용자가 특정 사용자를 팔로우합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<FollowDto> createFollow(
		FollowRequest followRequest,
		@Parameter(hidden = true) @AuthenticationPrincipal MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "사용자의 특정 사용자 팔로우 여부 조회",
		description = "사용자가 특정 사용자에 대한 팔로우 여부를 반환합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "해당 리소스 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<FollowDto> getFollowConnection(
		@Parameter(description = "팔로우 대상 사용자 ID") UUID followeeId,
		@Parameter(hidden = true) @AuthenticationPrincipal MoplUserDetails moplUserDetails
	);

	@Operation(
		summary = "특정 사용자의 팔로우 수 조회",
		description = "특정 사용자를 팔로우 하고 있는 사용자의 수를 반환합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Long> getFollowerCount(@Parameter(description = "팔로우 대상 사용자 ID") UUID followeeId);

	@Operation(
		summary = "팔로우 취소",
		description = "특정 사용자에 대한 팔로우를 취소합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> deleteFollow(
		@Parameter(description = "팔로우 ID") UUID followId,
		@Parameter(hidden = true) @AuthenticationPrincipal MoplUserDetails moplUserDetails
	);
}
