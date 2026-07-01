package com.team04.mopl.review.event;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.content.repository.ContentRepository;

@ExtendWith(MockitoExtension.class)
class ReviewRatingEventListenerTest {

	@Mock
	private ContentRepository contentRepository;

	@InjectMocks
	private ReviewRatingEventListener listener;

	@Test
	@DisplayName("refreshRatingAggregate 호출 시 업데이트된 행이 있으면 정상 처리된다")
	void handleReviewCreated_success_whenUpdated() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(1);

		// when
		listener.handleReviewCreated(new ReviewCreatedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("업데이트된 행이 0이면 warn 로그만 남기고 정상 종료된다")
	void handleReviewCreated_warn_whenNothingUpdated() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(0);

		// when
		listener.handleReviewCreated(new ReviewCreatedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("예외 발생 시 error 로그만 남기고 예외를 전파하지 않는다")
	void handleReviewCreated_doesNotThrow_whenExceptionOccurs() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId))
			.thenThrow(new RuntimeException("DB 오류"));

		// when & then
		listener.handleReviewCreated(new ReviewCreatedEvent(contentId));

		verify(contentRepository).refreshRatingAggregate(contentId);
	}
}
