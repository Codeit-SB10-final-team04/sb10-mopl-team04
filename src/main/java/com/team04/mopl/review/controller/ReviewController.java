package com.team04.mopl.review.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.service.ReviewService;

import jakarta.validation.Valid;
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

		return ResponseEntity.status(HttpStatus.OK).body(reviewDto);
	}

	@Override
	@PatchMapping("/{reviewId}")
	public ResponseEntity<ReviewDto> updateReview(
		@PathVariable UUID reviewId,
		@Valid @RequestBody ReviewUpdateRequest reviewUpdateRequest,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		ReviewDto reviewDto = reviewService.updateReview(reviewId, reviewUpdateRequest, moplUserDetails);

		return ResponseEntity.status(HttpStatus.OK).body(reviewDto);
	}

	@Override
	@DeleteMapping("/{reviewId}")
	public ResponseEntity<Void> deleteReview(
		@PathVariable UUID reviewId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		reviewService.deleteReview(reviewId, moplUserDetails);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
