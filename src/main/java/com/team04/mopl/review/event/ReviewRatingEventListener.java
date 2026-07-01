package com.team04.mopl.review.event;

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
	@Transactional(propagation = Propagation.REQUIRES_NEW) // 새 트랜잭션
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 커밋 이후에 실행하도록
	public void handleReviewCreated(ReviewCreatedEvent event) {
		try {
			int updated = contentRepository.refreshRatingAggregate(event.contentId());

			if (updated == 0) {
				log.warn("[REVIEW_RATING] 평점 업데이트 대상 없음: contentId={}", event.contentId());
				return;
			}

			log.info("[REVIEW_RATING] 평점 업데이트 완료: contentId={}", event.contentId());
		} catch (Exception e) {
			log.error("[REVIEW_RATING] 평점 업데이트 실패: contentId={}", event.contentId(), e);
		}
	}
}
