package com.team04.mopl.review.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.request.ReviewPageRequest;
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
import com.team04.mopl.review.dto.response.CursorResponseReviewDto;
import com.team04.mopl.review.dto.response.ReviewDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "리뷰 관리", description = "리뷰 관리 API")
public interface ReviewControllerDocs {
	@Operation(summary = "리뷰 목록 조회")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<CursorResponseReviewDto> getReviews(ReviewPageRequest request);

	@Operation(summary = "리뷰 등록")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<ReviewDto> createReview(ReviewCreateRequest reviewCreateRequest,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails);

	@Operation(summary = "리뷰 수정")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<ReviewDto> updateReview(@Parameter(description = "리뷰 ID") UUID reviewId,
		ReviewUpdateRequest reviewUpdateRequest,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails);

	@Operation(summary = "리뷰 삭제")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> deleteReview(@Parameter(description = "리뷰 ID") UUID reviewId,
		@Parameter(hidden = true) MoplUserDetails moplUserDetails);
}
