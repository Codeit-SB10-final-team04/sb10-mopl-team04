package com.team04.mopl.review.event;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.common.redis.DistributedLock;
import com.team04.mopl.content.repository.ContentRepository;

@ExtendWith(MockitoExtension.class)
class ReviewRatingEventListenerTest {

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private DistributedLock distributedLock;

	@InjectMocks
	private ReviewRatingEventListener listener;

	@BeforeEach
	void setUp() {
		// 분산 락이 항상 task를 실행하도록 mock
		when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(), any()))
			.thenAnswer(inv -> {
				Runnable task = inv.getArgument(3);
				task.run();
				return true;
			});
	}

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

	// ========== handleReviewUpdated ==========

	@Test
	@DisplayName("리뷰 수정 이벤트 수신 시 평점 재집계가 호출된다")
	void handleReviewUpdated_success_whenUpdated() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(1);

		// when
		listener.handleReviewUpdated(new ReviewUpdatedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("리뷰 수정 이벤트 처리 시 업데이트된 행이 0이면 warn 로그만 남기고 정상 종료된다")
	void handleReviewUpdated_warn_whenNothingUpdated() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(0);

		// when
		listener.handleReviewUpdated(new ReviewUpdatedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("리뷰 수정 이벤트 처리 시 예외 발생해도 전파하지 않는다")
	void handleReviewUpdated_doesNotThrow_whenExceptionOccurs() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId))
			.thenThrow(new RuntimeException("DB 오류"));

		// when & then
		listener.handleReviewUpdated(new ReviewUpdatedEvent(contentId));

		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	// ========== handleReviewDeleted ==========

	@Test
	@DisplayName("리뷰 삭제 이벤트 수신 시 평점 재집계가 호출된다")
	void handleReviewDeleted_success_whenDeleted() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(1);

		// when
		listener.handleReviewDeleted(new ReviewDeletedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("리뷰 삭제 이벤트 처리 시 업데이트된 행이 0이면 warn 로그만 남기고 정상 종료된다")
	void handleReviewDeleted_warn_whenNothingUpdated() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId)).thenReturn(0);

		// when
		listener.handleReviewDeleted(new ReviewDeletedEvent(contentId));

		// then
		verify(contentRepository).refreshRatingAggregate(contentId);
	}

	@Test
	@DisplayName("리뷰 삭제 이벤트 처리 시 예외 발생해도 전파하지 않는다")
	void handleReviewDeleted_doesNotThrow_whenExceptionOccurs() {
		// given
		UUID contentId = UUID.randomUUID();
		when(contentRepository.refreshRatingAggregate(contentId))
			.thenThrow(new RuntimeException("DB 오류"));

		// when & then
		listener.handleReviewDeleted(new ReviewDeletedEvent(contentId));

		verify(contentRepository).refreshRatingAggregate(contentId);
	}
}
