package com.team04.mopl.review.event;

import java.util.UUID;

import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team04.mopl.common.redis.DistributedLock;
import com.team04.mopl.content.repository.ContentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 리뷰 생성/수정/삭제 이벤트 수신 → 콘텐츠 평점 재계산
// contentId 기준 분산 락으로 동시 재계산 경합 방지
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewRatingEventListener {

	private final ContentRepository contentRepository;
	private final DistributedLock distributedLock;
	private final CacheManager cacheManager;

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

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleReviewDeleted(ReviewDeletedEvent event) {
		refreshRating(event.contentId());
	}

	private void refreshRating(UUID contentId) {
		distributedLock.executeWithLock("rating:" + contentId, 5, 10, () -> {
			try {
				int updated = contentRepository.refreshRatingAggregate(contentId);

				if (updated == 0) {
					log.warn("[REVIEW_RATING] 평점 업데이트 대상 없음: contentId={}", contentId);
					return;
				}

				// DB 업데이트 후 캐시 무효화 (averageRating 변경이 목록 정렬에 영향)
				var cache = cacheManager.getCache("contentList");
				if (cache != null) {
					cache.clear();
				}

				log.info("[REVIEW_RATING] 평점 업데이트 완료: contentId={}", contentId);
			} catch (Exception e) {
				log.error("[REVIEW_RATING] 평점 업데이트 실패: contentId={}", contentId, e);
			}
		});
	}
}
