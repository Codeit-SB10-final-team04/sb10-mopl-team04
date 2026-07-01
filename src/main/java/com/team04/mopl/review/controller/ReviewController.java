package com.team04.mopl.review.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.review.dto.requeset.ReviewCreateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.service.ReviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewControllerDocs {

	private final ReviewService reviewService;

	@Override
	@PostMapping
	public ResponseEntity<ReviewDto> createReview(@Valid @RequestBody ReviewCreateRequest reviewCreateRequest,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails) {

		ReviewDto reviewDto = reviewService.createReview(reviewCreateRequest, moplUserDetails);

		return ResponseEntity.ok(reviewDto);
	}
}
