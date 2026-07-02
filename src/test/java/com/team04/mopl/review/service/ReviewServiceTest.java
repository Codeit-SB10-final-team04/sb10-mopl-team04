package com.team04.mopl.review.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.event.ReviewCreatedEvent;
import com.team04.mopl.review.event.ReviewUpdatedEvent;
import com.team04.mopl.review.exception.ReviewException;
import com.team04.mopl.review.mapper.ReviewMapper;
import com.team04.mopl.review.repository.ReviewRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private ReviewMapper reviewMapper;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@InjectMocks
	private ReviewService reviewService;

	// ========== createReview ==========

	@Test
	@DisplayName("리뷰 등록에 성공하면 ReviewDto를 반환하고 이벤트를 발행한다")
	void createReview_returnReviewDto_whenValidRequest() {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);

		Content content = mock(Content.class);
		when(content.getId()).thenReturn(contentId);

		ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", (short)5);
		ReviewDto expectedDto = mock(ReviewDto.class);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(userId, contentId)).thenReturn(false);
		when(reviewMapper.toDto(any(Review.class), any())).thenReturn(expectedDto);

		// when
		ReviewDto result = reviewService.createReview(request, moplUserDetails);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(reviewRepository).save(any(Review.class));
		verify(applicationEventPublisher).publishEvent(any(ReviewCreatedEvent.class));
	}

	@Test
	@DisplayName("존재하지 않는 유저이면 UserException이 발생한다")
	void createReview_throwUserException_whenUserNotFound() {
		// given
		UUID userId = UUID.randomUUID();
		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "재밌어요", (short)5);

		// when & then
		assertThatThrownBy(() -> reviewService.createReview(request, moplUserDetails))
			.isInstanceOf(UserException.class);

		verify(reviewRepository, never()).save(any());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠이면 ContentException이 발생한다")
	void createReview_throwContentException_whenContentNotFound() {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.empty());

		ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", (short)5);

		// when & then
		assertThatThrownBy(() -> reviewService.createReview(request, moplUserDetails))
			.isInstanceOf(ContentException.class);

		verify(reviewRepository, never()).save(any());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("이미 리뷰를 작성한 경우 ReviewException이 발생한다")
	void createReview_throwReviewException_whenReviewAlreadyExists() {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);

		Content content = mock(Content.class);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(userId, contentId)).thenReturn(true);

		ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", (short)5);

		// when & then
		assertThatThrownBy(() -> reviewService.createReview(request, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(reviewRepository, never()).save(any());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("동시 요청으로 save 시 유니크 제약 충돌이 발생하면 ReviewException이 발생한다")
	void createReview_throwReviewException_whenDataIntegrityViolation() {
		// given
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);

		Content content = mock(Content.class);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(reviewRepository.existsByUserIdAndContentIdAndDeletedAtIsNull(userId, contentId)).thenReturn(false);
		when(reviewRepository.save(any(Review.class))).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

		ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", (short)5);

		// when & then
		assertThatThrownBy(() -> reviewService.createReview(request, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	// ========== updateReview ==========

	@Test
	@DisplayName("리뷰 수정에 성공하면 ReviewDto를 반환하고 이벤트를 발행한다")
	void updateReview_returnReviewDto_whenValidRequest() {
		// given
		UUID userId = UUID.randomUUID();
		UUID reviewId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);

		Content content = mock(Content.class);
		when(content.getId()).thenReturn(contentId);

		Review review = mock(Review.class);
		when(review.getUser()).thenReturn(user);
		when(review.getContent()).thenReturn(content);

		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);
		ReviewDto expectedDto = mock(ReviewDto.class);

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.of(review));
		when(reviewMapper.toDto(any(Review.class), any())).thenReturn(expectedDto);

		// when
		ReviewDto result = reviewService.updateReview(reviewId, request, moplUserDetails);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(review).update("수정된 리뷰", (short)4);
		verify(applicationEventPublisher).publishEvent(any(ReviewUpdatedEvent.class));
	}

	@Test
	@DisplayName("존재하지 않거나 삭제된 리뷰이면 ReviewException이 발생한다")
	void updateReview_throwReviewException_whenReviewNotFound() {
		// given
		UUID reviewId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(UUID.randomUUID());

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.empty());

		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);

		// when & then
		assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("리뷰 작성자가 아니면 ReviewException이 발생한다")
	void updateReview_throwReviewException_whenNotOwner() {
		// given
		UUID userId = UUID.randomUUID();
		UUID anotherUserId = UUID.randomUUID();
		UUID reviewId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User anotherUser = mock(User.class);
		when(anotherUser.getId()).thenReturn(anotherUserId);

		Review review = mock(Review.class);
		when(review.getId()).thenReturn(reviewId);
		when(review.getUser()).thenReturn(anotherUser);

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.of(review));

		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);

		// when & then
		assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(review, never()).update(any(), anyShort());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	// ========== deleteReview ==========

	@Test
	@DisplayName("리뷰 삭제에 성공하면 soft delete 처리 후 이벤트를 발행한다")
	void deleteReview_success_whenValidRequest() {
		// given
		UUID userId = UUID.randomUUID();
		UUID reviewId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);

		Content content = mock(Content.class);
		when(content.getId()).thenReturn(contentId);

		Review review = mock(Review.class);
		when(review.getUser()).thenReturn(user);
		when(review.getContent()).thenReturn(content);

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.of(review));

		// when
		reviewService.deleteReview(reviewId, moplUserDetails);

		// then
		verify(review).markDeleted(any(Instant.class));
		verify(applicationEventPublisher).publishEvent(any(ReviewUpdatedEvent.class));
	}

	@Test
	@DisplayName("존재하지 않거나 삭제된 리뷰이면 ReviewException이 발생한다")
	void deleteReview_throwReviewException_whenReviewNotFound() {
		// given
		UUID reviewId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(UUID.randomUUID());

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> reviewService.deleteReview(reviewId, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("리뷰 작성자가 아니면 ReviewException이 발생한다")
	void deleteReview_throwReviewException_whenNotOwner() {
		// given
		UUID userId = UUID.randomUUID();
		UUID anotherUserId = UUID.randomUUID();
		UUID reviewId = UUID.randomUUID();

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		when(moplUserDetails.getUserId()).thenReturn(userId);

		User anotherUser = mock(User.class);
		when(anotherUser.getId()).thenReturn(anotherUserId);

		Review review = mock(Review.class);
		when(review.getId()).thenReturn(reviewId);
		when(review.getUser()).thenReturn(anotherUser);

		when(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)).thenReturn(Optional.of(review));

		// when & then
		assertThatThrownBy(() -> reviewService.deleteReview(reviewId, moplUserDetails))
			.isInstanceOf(ReviewException.class);

		verify(review, never()).markDeleted(any());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}
}
