package com.team04.mopl.review.event;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.content.repository.ContentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewRatingEventListener {

	private final ContentRepository contentRepository;

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleReviewCreated(ReviewCreatedEvent event) {
		refreshRating(event.contentId());
	}

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleReviewUpdated(ReviewUpdatedEvent event) {
		refreshRating(event.contentId());
	}

	private void refreshRating(UUID contentId) {
		try {
			int updated = contentRepository.refreshRatingAggregate(contentId);

			if (updated == 0) {
				log.warn("[REVIEW_RATING] 평점 업데이트 대상 없음: contentId={}", contentId);
				return;
			}

			log.info("[REVIEW_RATING] 평점 업데이트 완료: contentId={}", contentId);
		} catch (Exception e) {
			log.error("[REVIEW_RATING] 평점 업데이트 실패: contentId={}", contentId, e);
		}
	}
}
