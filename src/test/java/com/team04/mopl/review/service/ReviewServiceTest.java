package com.team04.mopl.review.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.event.ReviewCreatedEvent;
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
}
