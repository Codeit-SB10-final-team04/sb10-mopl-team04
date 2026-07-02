package com.team04.mopl.review.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.request.ReviewPageRequest;
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
import com.team04.mopl.review.dto.response.CursorResponseReviewDto;
import com.team04.mopl.review.dto.response.ReviewCursorPage;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.enums.ReviewSortBy;
import com.team04.mopl.review.event.ReviewCreatedEvent;
import com.team04.mopl.review.event.ReviewDeletedEvent;
import com.team04.mopl.review.event.ReviewUpdatedEvent;
import com.team04.mopl.review.exception.ReviewErrorCode;
import com.team04.mopl.review.exception.ReviewException;
import com.team04.mopl.review.mapper.ReviewMapper;
import com.team04.mopl.review.repository.ReviewRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final UserRepository userRepository;
	private final ContentRepository contentRepository;
	private final ReviewMapper reviewMapper;
	private final ApplicationEventPublisher applicationEventPublisher;

	public CursorResponseReviewDto getReviews(ReviewPageRequest request) {
		SortDirection sortDirection = request.sortDirection();
		ReviewSortBy sortBy = request.sortBy();

		// 1. 조건에 따라 리뷰 목록 조회 (커서 기반 페이지네이션)
		ReviewCursorPage reviewCursorPage = reviewRepository.getReviews(request);

		// 2. Review 엔티티 → ReviewDto 변환
		List<ReviewDto> reviewDtoList = reviewCursorPage.reviewList().stream()
			.map(review -> {
				UserSummary userSummary = getUserSummary(review.getUser());
				return reviewMapper.toDto(review, userSummary);
			})
			.toList();

		boolean hasNext = reviewCursorPage.hasNext();
		List<Review> reviewList = reviewCursorPage.reviewList();

		// 3. 마지막 엔티티에서 다음 커서 값 계산
		Review lastReview = reviewList.isEmpty()
			? null
			: reviewList.get(reviewList.size() - 1);

		String nextCursor = hasNext && lastReview != null
			? (sortBy.equals(ReviewSortBy.createdAt)
			? lastReview.getCreatedAt().toString()
			: String.valueOf(lastReview.getRating()))
			: null;

		UUID nextIdAfter = hasNext && lastReview != null
			? lastReview.getId()
			: null;

		// 4. 커서 페이지네이션 응답 조립
		return new CursorResponseReviewDto(
			reviewDtoList,
			nextCursor,
			nextIdAfter,
			hasNext,
			reviewCursorPage.totalCount(),
			sortBy.toString(),
			sortDirection
		);
	}

	@Transactional
	public ReviewDto createReview(ReviewCreateRequest reviewCreateRequest, MoplUserDetails moplUserDetails) {
		// 요청자 Id 추출
		UUID userId = moplUserDetails.getUserId();

		log.info("[REVIEW_CREATE] 리뷰 생성 시작: userId={}, contentId={}", userId, reviewCreateRequest.contentId());

		// 요청자 검증
		User user = getUserOrThrow(userId);

		// 콘텐츠 검증
		Content content = getContentOrThrow(reviewCreateRequest.contentId());

		// 리뷰 존재 여부 확인(1인당 리뷰 한 개 제한)
		validateReviewByUserId(user.getId(), reviewCreateRequest.contentId());

		// 리뷰 생성 및 저장
		Review review = Review.builder()
			.user(user)
			.content(content)
			.rating(reviewCreateRequest.rating())
			.text(reviewCreateRequest.text())
			.build();
		try {
			reviewRepository.save(review);
		} catch (DataIntegrityViolationException e) {
			// 동시 요청으로 인한 유니크 제약 충돌 처리
			throw new ReviewException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
		}

		// User Summary 정보
		UserSummary userSummary = getUserSummary(user);

		// 콘텐츠 쪽 리뷰 개수 및 평균 평점 이벤트 발행(비동기 처리 -> 리뷰 많아질수록 집계 시간 오래 걸림)
		applicationEventPublisher.publishEvent(new ReviewCreatedEvent(content.getId()));

		log.info("[REVIEW_CREATE] 리뷰 생성 완료: userId={}, contentId={}, reviewId={}", userId, content.getId(),
			review.getId());

		return reviewMapper.toDto(review, userSummary);
	}

	@Transactional
	public ReviewDto updateReview(
		UUID reviewId,
		ReviewUpdateRequest reviewUpdateRequest,
		MoplUserDetails moplUserDetails
	) {
		UUID userId = moplUserDetails.getUserId();

		// 리뷰 검증 (존재, soft delete 여부)
		Review review = getReviewOrThrow(reviewId);

		// 오너 검증
		validateReviewOwner(review, userId);

		// 수정
		review.update(reviewUpdateRequest.text(), reviewUpdateRequest.rating());

		UserSummary userSummary = getUserSummary(review.getUser());

		// 리뷰 수정 이벤트 발행
		applicationEventPublisher.publishEvent(new ReviewUpdatedEvent(review.getContent().getId()));
		return reviewMapper.toDto(review, userSummary);
	}

	@Transactional
	public void deleteReview(UUID reviewId, MoplUserDetails moplUserDetails) {
		UUID userId = moplUserDetails.getUserId();

		log.info("[REVIEW_DELETE] 리뷰 삭제 시작: userId={}, reviewId={}", userId, reviewId);

		// 리뷰 검증
		Review review = getReviewOrThrow(reviewId);

		// 오너 검증
		validateReviewOwner(review, userId);

		// 리뷰 삭제 (논리)
		review.markDeleted(Instant.now());

		// 평점 재집계 이벤트 발행
		applicationEventPublisher.publishEvent(new ReviewDeletedEvent(review.getContent().getId()));

		log.info("[REVIEW_DELETE] 리뷰 삭제 완료: userId={}, reviewId={}", userId, reviewId);
	}

	// 사용자 엔티티 검증 및 반환
	private User getUserOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// 사용자 요약 정보 반환
	private UserSummary getUserSummary(User user) {
		return new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}

	// 콘텐츠 엔티티 검증 및 반환
	private Content getContentOrThrow(UUID contentId) {
		return contentRepository.findByIdAndDeletedAtIsNull(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
				.addDetail("contentId", contentId));
	}

	// 리뷰 엔티티 검증 및 반환 (soft delete 제외)
	private Review getReviewOrThrow(UUID reviewId) {
		return reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
			.orElseThrow(() -> new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND)
				.addDetail("reviewId", reviewId));
	}

	// 해당 콘텐츠 리뷰 1개 제한 확인
	private void validateReviewByUserId(UUID userId, UUID contentId) {
		if (reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(userId, contentId)) {
			throw new ReviewException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
		}
	}

	// 리뷰 오너 검증
	private void validateReviewOwner(Review review, UUID userId) {
		if (!review.getUser().getId().equals(userId)) {
			throw new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN)
				.addDetail("reviewId", review.getId())
				.addDetail("userId", userId);
		}
	}
}
