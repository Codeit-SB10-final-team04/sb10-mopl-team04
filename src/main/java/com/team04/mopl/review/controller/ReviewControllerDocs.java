package com.team04.mopl.review.controller;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "리뷰 API", description = "리뷰 관리")
public interface ReviewControllerDocs {
	@Operation(summary = "리뷰 등록")
	ResponseEntity<ReviewDto> createReview(ReviewCreateRequest reviewCreateRequest, MoplUserDetails moplUserDetails);
}
